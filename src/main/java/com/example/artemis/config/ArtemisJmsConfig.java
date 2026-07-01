package com.example.artemis.config;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.jms.JmsProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import com.example.artemis.service.ArtemisTopologyRebalancer;
import com.example.artemis.service.ClusterTopologyTracker;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Session;
import java.util.Arrays;
import java.util.List;

@Configuration
public class ArtemisJmsConfig {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisJmsConfig.class);

    @Value("${spring.jms.listener.concurrency:1}")
    private int minConcurrency;

    @Value("${spring.jms.listener.max-concurrency:10}")
    private int maxConcurrency;

    @Value("${spring.jms.listener.acknowledge-mode:client}")
    private String acknowledgeMode;

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            JmsProperties jmsProperties) {

        // Create a ThreadPoolTaskExecutor with a fixed number of threads equal to
        // maxConcurrency
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(maxConcurrency);
        executor.setMaxPoolSize(maxConcurrency);
        executor.setQueueCapacity(0);
        executor.initialize();

        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSessionAcknowledgeMode(toAcknowledgeMode(acknowledgeMode));
        factory.setConcurrency(minConcurrency + "-" + maxConcurrency);
        factory.setCacheLevel(DefaultMessageListenerContainer.CACHE_NONE);
        factory.setTaskExecutor(executor);
        factory.setErrorHandler(t -> logger.warn("JMS listener error (session will recover)", t));

        return factory;
    }

    // Bean for cluster topology tracking
    @Bean
    public ClusterTopologyTracker clusterTopologyTracker() {
        return new ClusterTopologyTracker();
    }

    // Bean for cluster topology rebalancing
    @Bean
    public ArtemisTopologyRebalancer artemisTopologyRebalancer(
            ConnectionFactory connectionFactory,
            JmsListenerEndpointRegistry jmsListenerEndpointRegistry,
            ClusterTopologyTracker clusterTopologyTracker,
            ApplicationEventPublisher eventPublisher) {

        return new ArtemisTopologyRebalancer(
                unwrap(connectionFactory),
                jmsListenerEndpointRegistry,
                clusterTopologyTracker,
                eventPublisher);
    }

    @Bean
    public RestTemplate restTemplate() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setSupportedMediaTypes(Arrays.asList(
                MediaType.APPLICATION_JSON,
                MediaType.TEXT_PLAIN,
                MediaType.APPLICATION_OCTET_STREAM));
        List<HttpMessageConverter<?>> converters = Arrays.asList(converter);
        return new RestTemplate(converters);
    }

    @Bean
    CommandLineRunner check(
            ConnectionFactory connectionFactory,
            JmsTemplate jmsTemplate,
            JmsListenerEndpointRegistry registry) {
        return args -> {
            logger.debug("---- JMS CONFIGURATION CHECK ----");

            try {
                jmsTemplate.execute(session -> null);
            } catch (Exception e) {
                logger.warn("Connection probe failed: {}", e.getMessage());
            }

            logger.debug("ConnectionFactory type: {}", connectionFactory.getClass().getName());

            if (connectionFactory instanceof JmsPoolConnectionFactory pool) {
                logger.debug("  maxConnections={} maxSessionsPerConnection={} blockIfFull={} blockIfFullTimeout(ms)={}",
                        pool.getMaxConnections(), 
                        pool.getMaxSessionsPerConnection(),
                        pool.isBlockIfSessionPoolIsFull(), 
                        pool.getBlockIfSessionPoolIsFullTimeout());
                logger.debug("  connectionIdleTimeout(ms)={} connectionCheckInterval(ms)={}",
                        pool.getConnectionIdleTimeout(), 
                        pool.getConnectionCheckInterval());
            }

            try {
                var locator = unwrap(connectionFactory).getServerLocator();
                logger.debug("  useTopologyForLoadBalancing={} isHA={}",
                        locator.getUseTopologyForLoadBalancing(), 
                        locator.isHA());
                logger.debug("  confirmationWindowSize={} consumerWindowSize={}",
                        locator.getConfirmationWindowSize(), 
                        locator.getConsumerWindowSize());
                logger.debug("  blockOnDurableSend={} blockOnNonDurableSend={} blockOnAcknowledge={}",
                        locator.isBlockOnDurableSend(), 
                        locator.isBlockOnNonDurableSend(),
                        locator.isBlockOnAcknowledge());
                logger.debug("  callTimeout(ms)={} callFailoverTimeout(ms)={} clientFailureCheckPeriod(ms)={}",
                        locator.getCallTimeout(), 
                        locator.getCallFailoverTimeout(),
                        locator.getClientFailureCheckPeriod());
                logger.debug("  connectionTTL(ms)={} connectionLoadBalancingPolicyClassName={}",
                        locator.getConnectionTTL(), 
                        locator.getConnectionLoadBalancingPolicyClassName());
                logger.debug("  useTopologyForLoadBalancing={} isHA={}",
                        locator.getUseTopologyForLoadBalancing(), 
                        locator.isHA());
                logger.debug(
                        "  initialConnectAttempts={} reconnectAttempts={} retryInterval={} retryIntervalMultiplier={} maxRetryInterval={}",
                        locator.getInitialConnectAttempts(), 
                        locator.getReconnectAttempts(),
                        locator.getRetryInterval(), 
                        locator.getRetryIntervalMultiplier(),
                        locator.getMaxRetryInterval());
            } catch (IllegalStateException e) {
                logger.warn("Could not unwrap for ServerLocator diagnostics: {}", e.getMessage());
            }

            logger.debug("JmsTemplate: sessionTransacted={} acknowledgeMode={} receiveTimeout(ms)={}",
                    jmsTemplate.isSessionTransacted(),
                    jmsTemplate.getSessionAcknowledgeMode(),
                    jmsTemplate.getReceiveTimeout());

            registry.getListenerContainers().forEach(c -> {
                if (c instanceof DefaultMessageListenerContainer dmlc) {
                    logger.debug("DMLC: minConcurrency={} maxConcurrency={} acknowledgeMode={} cacheLevel={}",
                            dmlc.getConcurrentConsumers(), 
                            dmlc.getMaxConcurrentConsumers(),
                            dmlc.getSessionAcknowledgeMode(), 
                            dmlc.getCacheLevel());
                }
            });

            logger.debug("---- END JMS CONFIGURATION CHECK ----");
        };
    }

    private static int toAcknowledgeMode(String mode) {
        return switch (mode.toLowerCase()) {
            case "auto"    -> Session.AUTO_ACKNOWLEDGE;
            case "client"  -> Session.CLIENT_ACKNOWLEDGE;
            case "dups-ok" -> Session.DUPS_OK_ACKNOWLEDGE;
            default -> throw new IllegalArgumentException(
                    "Unknown acknowledge-mode: '" + mode + "'. Valid: auto, client, dups-ok");
        };
    }

    private ActiveMQConnectionFactory unwrap(ConnectionFactory cf) {
        if (cf instanceof ActiveMQConnectionFactory amqCf) {
            return amqCf;
        } else if (cf instanceof JmsPoolConnectionFactory pool) {
            return unwrap((ConnectionFactory) pool.getConnectionFactory());
        } else if (cf instanceof org.springframework.jms.connection.CachingConnectionFactory caching) {
            return unwrap(caching.getTargetConnectionFactory());
        }
        throw new IllegalStateException(
                "Cannot unwrap ConnectionFactory of type: " + cf.getClass().getName());
    }
}
