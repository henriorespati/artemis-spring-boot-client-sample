package com.example.artemis;

import com.example.artemis.config.ArtemisProperties;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.*;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.*;

@Component
public class ArtemisCoreClientPool implements DisposableBean {

    private final ArtemisProperties properties;
    private ServerLocator serverLocator;
    private ClientSessionFactory sessionFactory;

    private final Map<String, BlockingQueue<ClientProducer>> producerPools = new ConcurrentHashMap<>();
    private final Map<String, BlockingQueue<ClientConsumer>> consumerPools = new ConcurrentHashMap<>();
    private final Map<String, ClientSession> sessions = new ConcurrentHashMap<>();

    private ExecutorService executor = Executors.newCachedThreadPool();

    public ArtemisCoreClientPool(ArtemisProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws Exception {
        List<String> brokerUrls = properties.getBrokerUrls();

        List<TransportConfiguration> transportConfigs = new ArrayList<>();
        for (String brokerUrl : brokerUrls) {
            Map<String, Object> params = new HashMap<>();
            String host;
            int port;
            String protocol;
            if (brokerUrl.startsWith("tcp://")) {
                protocol = "tcp";
                String addr = brokerUrl.substring(6);
                int colon = addr.indexOf(':');
                host = addr.substring(0, colon);
                port = Integer.parseInt(addr.substring(colon + 1));
            } else if (brokerUrl.startsWith("ssl://")) {
                protocol = "ssl";
                String addr = brokerUrl.substring(6);
                int colon = addr.indexOf(':');
                host = addr.substring(0, colon);
                port = Integer.parseInt(addr.substring(colon + 1));
            } else {
                throw new IllegalArgumentException("Unsupported protocol in broker URL: " + brokerUrl);
            }
            params.put("host", host);
            params.put("port", port);
            if ("ssl".equals(protocol)) {
                params.put("sslEnabled", true);
                params.put("trustStorePath", properties.getSsl().getTrustStorePath());
                params.put("trustStorePassword", properties.getSsl().getTrustStorePassword());
                params.put("keyStorePath", properties.getSsl().getKeyStorePath());
                params.put("keyStorePassword", properties.getSsl().getKeyStorePassword());
            }
            transportConfigs.add(new TransportConfiguration(NettyConnectorFactory.class.getName(), params));
        }

        serverLocator = ActiveMQClient.createServerLocatorWithoutHA(transportConfigs.toArray(new TransportConfiguration[0]));
        serverLocator.setReconnectAttempts(-1); // infinite reconnect
        sessionFactory = serverLocator.createSessionFactory();

        for (ArtemisProperties.QueueConfig q : properties.getQueues()) {
            String queueName = q.getName();
            SimpleString ssQueueName = SimpleString.toSimpleString(queueName);
            ClientSession session = sessionFactory.createSession(false, true, true);
            sessions.put(queueName, session);

            // Create queue if not exists
            try {
                if (!session.queueQuery(ssQueueName).isExists()) {
                    QueueConfiguration qc = new QueueConfiguration(ssQueueName)
                            .setAddress(ssQueueName)
                            .setDurable(true);
                    session.createQueue(qc);
                }
            } catch (ActiveMQException e) {
                // ignore if exists
            }

            // Producer pool
            BlockingQueue<ClientProducer> producerPool = new LinkedBlockingQueue<>(properties.getPool().getMaxProducers());
            for (int i = 0; i < properties.getPool().getMaxProducers(); i++) {
                ClientProducer producer = session.createProducer(queueName);
                producerPool.offer(producer);
            }
            producerPools.put(queueName, producerPool);

            // Consumer pool
            BlockingQueue<ClientConsumer> consumerPool = new LinkedBlockingQueue<>(properties.getPool().getMaxConsumers());
            for (int i = 0; i < properties.getPool().getMaxConsumers(); i++) {
                ClientConsumer consumer = session.createConsumer(queueName);
                consumerPool.offer(consumer);
            }
            consumerPools.put(queueName, consumerPool);
        }
    }

    public void send(String queue, String message) throws Exception {
        ClientProducer producer = producerPools.get(queue).poll(1, TimeUnit.SECONDS);
        if (producer == null) {
            throw new IllegalStateException("No available producer for queue " + queue);
        }
        try {
            ClientMessage msg = sessions.get(queue).createMessage(true);
            msg.getBodyBuffer().writeString(message);
            producer.send(msg);
        } finally {
            producerPools.get(queue).offer(producer);
        }
    }

    @Async
    public CompletableFuture<Void> sendAsync(String queue, String message) {
        return CompletableFuture.runAsync(() -> {
            try {
                send(queue, message);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    public ClientMessage receive(String queue) throws Exception {
        ClientConsumer consumer = consumerPools.get(queue).poll(1, TimeUnit.SECONDS);
        if (consumer == null) {
            throw new IllegalStateException("No available consumer for queue " + queue);
        }
        try {
            ClientMessage msg = consumer.receive(properties.getPool().getConsumerReceiveTimeoutMs());
            if (msg != null) {
                msg.acknowledge();
            }
            return msg;
        } finally {
            consumerPools.get(queue).offer(consumer);
        }
    }

    @Async
    public CompletableFuture<ClientMessage> receiveAsync(String queue) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return receive(queue);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    @Override
    public void destroy() throws Exception {
        for (ClientSession session : sessions.values()) {
            session.close();
        }
        if (sessionFactory != null) {
            sessionFactory.close();
        }
        if (serverLocator != null) {
            serverLocator.close();
        }
        executor.shutdownNow();
    }
}
