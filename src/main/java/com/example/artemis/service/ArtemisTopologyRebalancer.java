package com.example.artemis.service;

import com.example.artemis.event.TopologyChangeEvent;
import com.example.artemis.listener.TopologyRebalanceListener;

import jakarta.jms.Connection;
import jakarta.jms.JMSException;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.MessageListenerContainer;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;



/**
 * Rebalances JMS consumer connections across Artemis cluster nodes on topology changes.
 *
 * <p>Artemis's built-in {@code RoundRobinConnectionLoadBalancingPolicy} consistently routes
 * all DMLC sessions to the same broker because DMLC shares one {@code ClientSessionFactory}
 * (one TCP connection) per container regardless of {@code CACHE_NONE}. To force distribution,
 * this class queries each broker's {@code ConsumerCount} via Jolokia on each rebalance, picks
 * the least loaded node, and replaces the container's {@code ConnectionFactory} with a
 * single-broker CF targeting that node.
 *
 * <p>A persistent {@link #topologyMonitorConnection} on the original CF keeps the
 * {@code ServerLocator} alive after the container switches to a targeted CF, ensuring
 * {@code nodeDown}/{@code nodeUP} events continue to arrive.
 *
 * <p>Broker nodes are auto-derived from {@code spring.artemis.broker-url} using
 * {@code spring.jms.rebalance.management-port-offset} (default 53455), or overridden
 * explicitly via {@code spring.jms.rebalance.broker-nodes=host:mgmtPort:brokerPort,...}.
 * Credentials default to {@code spring.artemis.user} / {@code spring.artemis.password}.
 */
public class ArtemisTopologyRebalancer implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ArtemisTopologyRebalancer.class);

    @Value("${spring.jms.rebalance.debounce.node-up-ms:15000}")
    private long nodeUpDebounceMs = 15_000;

    @Value("${spring.jms.rebalance.debounce.node-down-ms:5000}")
    private long nodeDownDebounceMs = 5_000;

    @Value("${spring.jms.rebalance.stagger-delay-ms:2000}")
    private long staggerDelayMs = 2_000;

    @Value("${spring.jms.rebalance.startup-grace-period-ms:3000}")
    private long startupGracePeriodMs = 3_000;

    /** Derived from {@code spring.artemis.broker-url}. */
    private String brokerNodesConfig = "";

    /** Subtracted from broker port to get management port. Default: 61616→8161, 61716→8261, 61816→8361. */
    @Value("${spring.jms.rebalance.management-port-offset:53455}")
    private int managementPortOffset = 53455;

    @Value("${spring.artemis.broker-url:tcp://localhost:61616}")
    private String artemisBrokerUrl = "tcp://localhost:61616";

    @Value("${spring.jms.rebalance.management-username:${spring.artemis.user:admin}}")
    private String managementUsername = "admin";

    @Value("${spring.jms.rebalance.management-password:${spring.artemis.password:admin}}")
    private String managementPassword = "admin";

    private final ActiveMQConnectionFactory   connectionFactory;
    private final JmsListenerEndpointRegistry registry;
    private final ClusterTopologyTracker      topologyTracker;
    private final ApplicationEventPublisher   eventPublisher;

    @Autowired(required = false)
    private RestTemplate restTemplate;

    public ArtemisTopologyRebalancer(
            ActiveMQConnectionFactory connectionFactory,
            JmsListenerEndpointRegistry registry,
            ClusterTopologyTracker topologyTracker,
            ApplicationEventPublisher eventPublisher) {
        this.connectionFactory = connectionFactory;
        this.registry          = registry;
        this.topologyTracker   = topologyTracker;
        this.eventPublisher    = eventPublisher;
    }

    private final AtomicBoolean rebalancing = new AtomicBoolean(false);
    private volatile long lastRebalanceMs = 0;

    /**
     * Keeps the original CF's {@code ServerLocator} alive after containers switch to targeted CFs.
     * Without this, the ServerLocator loses all active factories and stops receiving topology events.
     */
    private volatile Connection topologyMonitorConnection;

    @Override
    public void afterPropertiesSet() {
        connectionFactory.getServerLocator().addClusterTopologyListener(
                new TopologyRebalanceListener(topologyTracker, eventPublisher, startupGracePeriodMs));

        try {
            topologyMonitorConnection = connectionFactory.createConnection();
            topologyMonitorConnection.start();
            log.info("Topology monitor connection established on original CF");
        } catch (JMSException e) {
            log.error("Failed to create topology monitor connection — topology events may stop after rebalance", e);
        }

        List<BrokerNode> nodes = parseBrokerNodes();
        log.info("ArtemisTopologyRebalancer initialised. nodeUpDebounce={}ms nodeDownDebounce={}ms " +
                 "staggerDelay={}ms startupGracePeriod={}ms brokerNodes={} ({})",
                 nodeUpDebounceMs, nodeDownDebounceMs, staggerDelayMs, startupGracePeriodMs,
                 nodes,
                 brokerNodesConfig.isBlank()
                     ? "derived from spring.artemis.broker-url, offset=" + managementPortOffset
                     : "explicit via spring.jms.rebalance.broker-nodes");
    }

    @Override
    public void destroy() {
        log.info("ArtemisTopologyRebalancer shutting down");
        if (topologyMonitorConnection != null) {
            try { topologyMonitorConnection.close(); } catch (JMSException e) {
                log.warn("Error closing topology monitor connection", e);
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        tryRebalance("startup", startupGracePeriodMs);
    }

    @EventListener
    public void onTopologyChange(TopologyChangeEvent event) {
        if (event.isInitialBroadcast()) {
            log.debug("Ignoring initial broadcast: {}", event);
            return;
        }
        log.info("Topology change received: {}", event);
        long debounceMs = event.getType() == TopologyChangeEvent.Type.NODE_UP
                ? nodeUpDebounceMs : nodeDownDebounceMs;
        tryRebalance(event.toString(), debounceMs);
    }

    private void tryRebalance(String reason, long debounceMs) {
        long now = System.currentTimeMillis();
        if (now - lastRebalanceMs < debounceMs) {
            log.info("Rebalance debounced ({}ms elapsed < {}ms threshold). Reason: {}",
                    now - lastRebalanceMs, debounceMs, reason);
            return;
        }
        lastRebalanceMs = now;
        log.info("Executing rebalance. Reason: {}", reason);
        executeRebalance();
    }

    private void executeRebalance() {
        if (!rebalancing.compareAndSet(false, true)) {
            log.warn("Rebalance already in progress — skipping");
            return;
        }
        try {
            if (!topologyTracker.isClusterHealthy()) {
                log.warn("Rebalance skipped — only {} active node(s)", topologyTracker.getActiveNodeCount());
                return;
            }
            List<DefaultMessageListenerContainer> containers = getRunningContainers();
            if (containers.isEmpty()) {
                log.warn("Rebalance skipped — no running containers");
                return;
            }
            log.info("Starting rebalance across {} container(s), {} active node(s)",
                    containers.size(), topologyTracker.getActiveNodeCount());
            for (DefaultMessageListenerContainer container : containers) {
                rebalanceContainer(container);
            }
            log.info("Rebalance complete");
        } finally {
            rebalancing.set(false);
        }
    }

    /**
     * Selects the least loaded broker via Jolokia, creates a single-broker CF targeting it,
     * and bounces the container onto that CF. Falls back to stop/start on the original CF
     * when Jolokia is unavailable.
     */
    private void rebalanceContainer(DefaultMessageListenerContainer container) {
        String destination = container.getDestinationName();
        if (!container.isRunning()) {
            log.warn("Skipping container [{}] — not running", destination);
            return;
        }

        BrokerNode target = selectLeastLoadedBroker(destination);
        if (target == null) {
            log.warn("Could not determine least loaded broker for [{}] — falling back to original CF", destination);
            doStopStart(container, connectionFactory);
            return;
        }

        log.info("Rebalancing container [{}]: activeConsumers={} → targeting {} (fewest consumers)",
                destination, container.getActiveConsumerCount(), target);

        ActiveMQConnectionFactory targetedCf = new ActiveMQConnectionFactory(
                "tcp://" + target.host + ":" + target.brokerPort +
                "?ha=true&reconnectAttempts=30&retryInterval=1000&retryIntervalMultiplier=2.0&maxRetryInterval=30000");

        doStopStart(container, targetedCf);

        log.info("Container [{}] now targeting {}: active={} scheduled={}",
                destination, target, container.getActiveConsumerCount(), container.getScheduledConsumerCount());
    }

    private void doStopStart(DefaultMessageListenerContainer container, jakarta.jms.ConnectionFactory cf) {
        container.stop();
        sleep(staggerDelayMs);
        container.setConnectionFactory(cf);
        container.start();
    }

    private BrokerNode selectLeastLoadedBroker(String queueName) {
        if (restTemplate == null) {
            log.warn("RestTemplate not available — add a RestTemplate @Bean to your config");
            return null;
        }
        List<BrokerNode> nodes = parseBrokerNodes();
        if (nodes.isEmpty()) {
            log.warn("No broker nodes available for Jolokia query");
            return null;
        }

        BrokerNode leastLoaded = null;
        int        minCount    = Integer.MAX_VALUE;

        for (BrokerNode node : nodes) {
            try {
                int count = queryConsumerCount(node, queueName);
                log.debug("Broker {} — ConsumerCount[{}]={}", node, queueName, count);
                if (count < minCount) {
                    minCount    = count;
                    leastLoaded = node;
                }
            } catch (Exception e) {
                log.warn("Failed to query ConsumerCount on {} — treating as max load: {}", node, e.getMessage());
            }
        }

        if (leastLoaded != null) {
            log.info("Selected broker {} with {} consumers on [{}]", leastLoaded, minCount, queueName);
        }
        return leastLoaded;
    }

    private int queryConsumerCount(BrokerNode node, String queueName) {
        String encQueue = URLEncoder.encode(queueName, StandardCharsets.UTF_8);
        String encHost  = URLEncoder.encode(node.host, StandardCharsets.UTF_8);
        String mbean    = "org.apache.activemq.artemis:address=%22" + encQueue +
                          "%22,broker=%22" + encHost +
                          "%22,component=addresses,queue=%22" + encQueue +
                          "%22,routing-type=%22anycast%22,subcomponent=queues";
        String url      = "http://" + node.host + ":" + node.managementPort +
                          "/console/jolokia/read/" + mbean + "/ConsumerCount";

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new org.springframework.core.ParameterizedTypeReference<>() {});
        if (resp.getBody() != null) {
            Object value = resp.getBody().get("value");
            if (value != null) return Integer.parseInt(value.toString());
        }
        return 0;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Basic " + Base64.getEncoder().encodeToString(
                (managementUsername + ":" + managementPassword).getBytes(StandardCharsets.UTF_8)));
        return h;
    }

    /** Returns broker nodes from explicit config or auto-derived from {@code spring.artemis.broker-url}. */
    private List<BrokerNode> parseBrokerNodes() {
        if (!brokerNodesConfig.isBlank()) {
            return Arrays.stream(brokerNodesConfig.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(s -> {
                        String[] p = s.split(":");
                        if (p.length != 3) {
                            log.warn("Invalid broker-node '{}' — expected host:mgmtPort:brokerPort", s);
                            return null;
                        }
                        try { return new BrokerNode(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2])); }
                        catch (NumberFormatException e) {
                            log.warn("Non-numeric port in broker-node '{}': {}", s, e.getMessage());
                            return null;
                        }
                    })
                    .filter(Objects::nonNull).collect(Collectors.toList());
        }
        return deriveFromArtemisUrl();
    }

    /** Parses {@code spring.artemis.broker-url} — handles both single and multi-broker forms. */
    private List<BrokerNode> deriveFromArtemisUrl() {
        String stripped = artemisBrokerUrl
                .replaceAll("^\\s*\\(", "")
                .replaceAll("\\).*$",   "")
                .replaceAll("\\?.*$",   "");

        List<BrokerNode> nodes = Arrays.stream(stripped.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.replaceFirst("(?i)tcp://", ""))
                .map(s -> {
                    String[] p = s.replaceAll("\\?.*$", "").split(":");
                    if (p.length < 2) {
                        log.warn("Cannot parse host:port from '{}' in spring.artemis.broker-url", s);
                        return null;
                    }
                    try {
                        int brokerPort = Integer.parseInt(p[1]);
                        return new BrokerNode(p[0], brokerPort - managementPortOffset, brokerPort);
                    } catch (NumberFormatException e) {
                        log.warn("Non-numeric port in '{}': {}", s, e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull).collect(Collectors.toList());

        log.debug("Derived {} broker node(s) from spring.artemis.broker-url (offset={}): {}",
                nodes.size(), managementPortOffset, nodes);
        return nodes;
    }

    private List<DefaultMessageListenerContainer> getRunningContainers() {
        Collection<MessageListenerContainer> all = registry.getListenerContainers();
        List<DefaultMessageListenerContainer> running = all.stream()
                .filter(c -> c instanceof DefaultMessageListenerContainer)
                .map(c -> (DefaultMessageListenerContainer) c)
                .filter(DefaultMessageListenerContainer::isRunning)
                .collect(Collectors.toList());
        log.debug("Found {} running container(s) out of {} total", running.size(), all.size());
        return running;
    }

    public boolean isRebalancing()  { return rebalancing.get(); }
    public int getActiveNodeCount() { return topologyTracker.getActiveNodeCount(); }

    private static class BrokerNode {
        final String host;
        final int    managementPort;
        final int    brokerPort;

        BrokerNode(String host, int managementPort, int brokerPort) {
            this.host           = host;
            this.managementPort = managementPort;
            this.brokerPort     = brokerPort;
        }

        @Override public String toString() {
            return host + ":" + brokerPort + " (mgmt:" + managementPort + ")";
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Rebalance sleep interrupted");
        }
    }
}