package com.example.artemis.config;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.jms.JmsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Session;

@Configuration
public class ArtemisJmsConfig {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisJmsConfig.class);

    @Value("${spring.jms.listener.concurrency:1}")
    private int minConcurrency;
 
    @Value("${spring.jms.listener.max-concurrency:10}")
    private int maxConcurrency;

    @Value("${spring.jms.listener.acknowledge-mode:client}")
    private String acknowledgeMode;

//     @Autowired
//     private DefaultJmsListenerContainerFactory jmsListenerContainerFactory;

    @Autowired
    private JmsTemplate jmsTemplate;

    // Configure fixed thread pool size equal to maxConcurrency to prevent unbounded thread creation
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
            ConnectionFactory connectionFactory,
            JmsProperties jmsProperties) {

        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSessionAcknowledgeMode(toAcknowledgeMode(acknowledgeMode));
        factory.setConcurrency(minConcurrency + "-" + maxConcurrency);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(maxConcurrency);
        executor.setMaxPoolSize(maxConcurrency);
        executor.setQueueCapacity(0);
        executor.initialize();

        factory.setTaskExecutor(executor);
        factory.setErrorHandler(t -> logger.warn("JMS listener error (session will recover)", t));

        return factory;
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    } 

    // Log JMS configuration on startup
    @Bean
    CommandLineRunner check(
            JmsPoolConnectionFactory pool,
            JmsTemplate jmsTemplate,
            JmsListenerEndpointRegistry registry
    ) {
        return args -> {
            logger.debug("---- JMS CONFIGURATION CHECK ----");

            // Force a connection to initialize the pool + delegate
            try {
                jmsTemplate.execute(session -> {
                    logger.debug("Forcing connection creation for diagnostics...");
                    return null;
                });
            } catch (Exception e) {
                logger.warn("Dummy connection attempt failed: {}", e.getMessage());
            }

            // ConnectionFactory
            logger.debug("ConnectionFactory type: {}", pool.getClass().getName());

            // Pooled ConnectionFactory settings
            logger.debug("JmsPoolConnectionFactory settings:");
            logger.debug("  maxConnections={} maxSessionsPerConnection={} blockIfFull={} blockIfFullTimeout(ms)={}",
                    pool.getMaxConnections(),
                    pool.getMaxSessionsPerConnection(),
                    pool.isBlockIfSessionPoolIsFull(),
                    pool.getBlockIfSessionPoolIsFullTimeout()
            );
            logger.debug("  connectionIdleTimeout(ms)={} connectionCheckInterval(ms)={} useProviderJMSContext={}",
                    pool.getConnectionIdleTimeout(),
                    pool.getConnectionCheckInterval(),
                    pool.isUseProviderJMSContext()
            );
            logger.debug("  numConnectionsInUse={}", pool.getNumConnections());

            // Delegate factory
            var delegate = pool.getConnectionFactory();
            logger.debug("Delegate factory type: {}", delegate.getClass().getName());

            if (delegate instanceof ActiveMQConnectionFactory amq) {
                var locator = amq.getServerLocator();

                // --- Artemis ServerLocator settings ---
                logger.debug("Artemis ServerLocator settings:");
                logger.debug("  confirmationWindowSize={} consumerWindowSize={}",
                        locator.getConfirmationWindowSize(),
                        locator.getConsumerWindowSize()
                );
                logger.debug("  blockOnDurableSend={} blockOnNonDurableSend={} blockOnAcknowledge={}",
                        locator.isBlockOnDurableSend(),
                        locator.isBlockOnNonDurableSend(),
                        locator.isBlockOnAcknowledge()
                );
                logger.debug("  ackBatchSize={}",
                        locator.getAckBatchSize()
                );
                logger.debug("  producerMaxRate={} consumerMaxRate={}",
                        locator.getProducerMaxRate(),
                        locator.getConsumerMaxRate()
                );
                logger.debug("  callTimeout(ms)={} callFailoverTimeout(ms)={} clientFailureCheckPeriod(ms)={}",
                        locator.getCallTimeout(),
                        locator.getCallFailoverTimeout(),
                        locator.getClientFailureCheckPeriod()
                );
                logger.debug("  connectionTTL(ms)={} connectionLoadBalancingPolicyClassName={}",
                        locator.getConnectionTTL(),
                        locator.getConnectionLoadBalancingPolicyClassName()
                );
                logger.debug("  minLargeMessageSize={}",
                        locator.getMinLargeMessageSize()
                );
                logger.debug("  useGlobalPools={} scheduledThreadPoolMaxSize={} threadPoolMaxSize={}",
                        locator.isUseGlobalPools(),
                        locator.getScheduledThreadPoolMaxSize(),
                        locator.getThreadPoolMaxSize()
                );
                logger.debug("  initialConnectAttempts={} reconnectAttempts={} retryInterval={} retryIntervalMultiplier={} maxRetryInterval={}",
                        locator.getInitialConnectAttempts(),
                        locator.getReconnectAttempts(),
                        locator.getRetryInterval(),
                        locator.getRetryIntervalMultiplier(),
                        locator.getMaxRetryInterval()
                );
            }            

            // JmsTemplate
            logger.debug("JmsTemplate settings:");
            logger.debug("  sessionTransacted={} acknowledgeMode={} receiveTimeout(ms)={}",
                    jmsTemplate.isSessionTransacted(),
                    jmsTemplate.getSessionAcknowledgeMode(),
                    jmsTemplate.getReceiveTimeout()
            );

            // JMS Listener Container Factory
            logger.debug("JmsListenerContainerFactory settings:");
            if (!registry.getListenerContainers().isEmpty()) {
                registry.getListenerContainers().forEach(container -> {
                    if (container instanceof DefaultMessageListenerContainer dmlc) {
                        logger.debug("  minConcurrency={} maxConcurrency={} acknowledgeMode={}",
                                dmlc.getConcurrentConsumers(),
                                dmlc.getMaxConcurrentConsumers(),
                                dmlc.getSessionAcknowledgeMode()
                        );
                    } 
                });
            } else {
                logger.debug("No listener containers registered yet");
            }

            logger.debug("---- END JMS CONFIGURATION CHECK ----");
        };
    }

    private static int toAcknowledgeMode(String mode) {
        return switch (mode.toLowerCase()) {
            case "auto"     -> Session.AUTO_ACKNOWLEDGE;
            case "client"   -> Session.CLIENT_ACKNOWLEDGE;
            case "dups-ok"  -> Session.DUPS_OK_ACKNOWLEDGE;
            default -> throw new IllegalArgumentException(
                    "Unknown spring.jms.listener.acknowledge-mode: '" + mode +
                    "'. Valid values: auto, client, dups-ok");
        };
    }
}
