package com.example.artemis.config;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.jms.core.JmsTemplate;

import jakarta.jms.Session;

@Configuration
public class ArtemisJmsConfig {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisJmsConfig.class);

    @Value("${spring.artemis.user}")
    private String artemisUser;

    @Value("${spring.artemis.password}")
    private String artemisPassword;

    @Value("${spring.artemis.broker-url}")
    private String brokerUrl;

    @Value("${spring.artemis.pool.max-connections}")
    private int poolMaxConnections;

    @Value("${spring.artemis.pool.max-sessions-per-connection}")
    private int poolMaxSessionsPerConnection;

    @Value("${spring.jms.listener.min-concurrency}")
    private int listenerMinConcurrency;

    @Value("${spring.jms.listener.max-concurrency}")
    private int listenerMaxConcurrency;

    @Value("${spring.jms.template.receive-timeout}")
    private int templateReceiveTimeout;

    @Bean
    CommandLineRunner check(
            JmsPoolConnectionFactory pool,
            JmsTemplate jmsTemplate,
            DefaultJmsListenerContainerFactory jmsListenerContainerFactory
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

            // --- Pooled ConnectionFactory settings ---
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
                logger.debug("  reconnectAttempts={} retryInterval={} retryIntervalMultiplier={} maxRetryInterval={}",
                        locator.getReconnectAttempts(),
                        locator.getRetryInterval(),
                        locator.getRetryIntervalMultiplier(),
                        locator.getMaxRetryInterval()
                );
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
                logger.debug("  autoGroup={} preAcknowledge={} cacheLargeMessagesClient={}",
                        locator.isAutoGroup(),
                        locator.isPreAcknowledge(),
                        locator.isCacheLargeMessagesClient()
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

            logger.debug("---- END JMS CONFIGURATION CHECK ----");
        };
    }


    @Bean
    public JmsPoolConnectionFactory pooledConnectionFactory() {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        factory.setUser(artemisUser);
        factory.setPassword(artemisPassword);

        JmsPoolConnectionFactory pool = new JmsPoolConnectionFactory();
        pool.setConnectionFactory(factory);
        pool.setMaxConnections(poolMaxConnections);
        pool.setMaxSessionsPerConnection(poolMaxSessionsPerConnection);
        return pool;
    }

    @Primary
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(JmsPoolConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE); // for SYNC listener
        // factory.setSessionAcknowledgeMode(Session.AUTO_ACKNOWLEDGE); // for ASYNC listener
        factory.setConcurrency(listenerMinConcurrency + "-" + listenerMaxConcurrency);
        return factory;
    }

    @Bean
    public JmsTransactionManager jmsTransactionManager(JmsPoolConnectionFactory connectionFactory) {
        return new JmsTransactionManager(connectionFactory);
    }

    // Transactional listener container factory
    @Bean
    public DefaultJmsListenerContainerFactory txJmsListenerContainerFactory(
            JmsPoolConnectionFactory connectionFactory, JmsTransactionManager transactionManager) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrency(listenerMinConcurrency + "-" + listenerMaxConcurrency);
        factory.setSessionTransacted(true);
        factory.setTransactionManager(transactionManager);
        return factory;
    }

    @Bean
    public JmsTemplate jmsTemplate(JmsPoolConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setReceiveTimeout(templateReceiveTimeout);
        return template;
    }

}
