package com.example.artemis.config;

import jakarta.jms.ConnectionFactory;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
@Profile("single-broker")
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

    @Value("${spring.jms.listener.session.transacted}")
    private boolean listenerTransacted;

    @Value("${spring.jms.template.receive-timeout}")
    private int templateReceiveTimeout;

    @Value("${spring.jms.template.session.transacted}")
    private boolean templateTransacted;

    @Bean
    CommandLineRunner check(
            JmsPoolConnectionFactory pool,
            JmsTemplate jmsTemplate,
            DefaultJmsListenerContainerFactory jmsListenerContainerFactory
    ) {
        return args -> {
            logger.info("---- JMS CONFIGURATION CHECK ----");

            // Force a connection to initialize the pool + delegate
            try {
                jmsTemplate.execute(session -> {
                    logger.info("Forcing connection creation for diagnostics...");
                    return null;
                });
            } catch (Exception e) {
                logger.warn("Dummy connection attempt failed: {}", e.getMessage());
            }

            // ConnectionFactory
            logger.info("ConnectionFactory type: {}", pool.getClass().getName());

            // --- Pooled ConnectionFactory settings ---
            logger.info("JmsPoolConnectionFactory settings:");
            logger.info("  maxConnections={} maxSessionsPerConnection={} blockIfFull={} blockIfFullTimeout(ms)={}",
                    pool.getMaxConnections(),
                    pool.getMaxSessionsPerConnection(),
                    pool.isBlockIfSessionPoolIsFull(),
                    pool.getBlockIfSessionPoolIsFullTimeout()
            );
            logger.info("  connectionIdleTimeout(ms)={} connectionCheckInterval(ms)={} useProviderJMSContext={}",
                    pool.getConnectionIdleTimeout(),
                    pool.getConnectionCheckInterval(),
                    pool.isUseProviderJMSContext()
            );
            logger.info("  numConnectionsInUse={}", pool.getNumConnections());

            // Delegate factory
            var delegate = pool.getConnectionFactory();
            logger.info("Delegate factory type: {}", delegate.getClass().getName());

            if (delegate instanceof ActiveMQConnectionFactory amq) {
                var locator = amq.getServerLocator();

                // --- Artemis ServerLocator settings ---
                logger.info("Artemis ServerLocator settings:");
                logger.info("  reconnectAttempts={} retryInterval={} retryIntervalMultiplier={} maxRetryInterval={}",
                        locator.getReconnectAttempts(),
                        locator.getRetryInterval(),
                        locator.getRetryIntervalMultiplier(),
                        locator.getMaxRetryInterval()
                );
                logger.info("  confirmationWindowSize={} consumerWindowSize={}",
                        locator.getConfirmationWindowSize(),
                        locator.getConsumerWindowSize()
                );
                logger.info("  blockOnDurableSend={} blockOnNonDurableSend={} blockOnAcknowledge={}",
                        locator.isBlockOnDurableSend(),
                        locator.isBlockOnNonDurableSend(),
                        locator.isBlockOnAcknowledge()
                );
                logger.info("  ackBatchSize={}",
                        locator.getAckBatchSize()
                );
                logger.info("  producerMaxRate={} consumerMaxRate={}",
                        locator.getProducerMaxRate(),
                        locator.getConsumerMaxRate()
                );
                logger.info("  callTimeout(ms)={} callFailoverTimeout(ms)={} clientFailureCheckPeriod(ms)={}",
                        locator.getCallTimeout(),
                        locator.getCallFailoverTimeout(),
                        locator.getClientFailureCheckPeriod()
                );
                logger.info("  connectionTTL(ms)={} connectionLoadBalancingPolicyClassName={}",
                        locator.getConnectionTTL(),
                        locator.getConnectionLoadBalancingPolicyClassName()
                );
                logger.info("  minLargeMessageSize={}",
                        locator.getMinLargeMessageSize()
                );
                logger.info("  useGlobalPools={} scheduledThreadPoolMaxSize={} threadPoolMaxSize={}",
                        locator.isUseGlobalPools(),
                        locator.getScheduledThreadPoolMaxSize(),
                        locator.getThreadPoolMaxSize()
                );
                logger.info("  autoGroup={} preAcknowledge={} cacheLargeMessagesClient={}",
                        locator.isAutoGroup(),
                        locator.isPreAcknowledge(),
                        locator.isCacheLargeMessagesClient()
                );
                logger.info("  initialConnectAttempts={} reconnectAttempts={} retryInterval={} retryIntervalMultiplier={} maxRetryInterval={}",
                        locator.getInitialConnectAttempts(),
                        locator.getReconnectAttempts(),
                        locator.getRetryInterval(),
                        locator.getRetryIntervalMultiplier(),
                        locator.getMaxRetryInterval()
                );
            }            

            // JmsTemplate
            logger.info("JmsTemplate settings:");
            logger.info("  sessionTransacted={} acknowledgeMode={} receiveTimeout(ms)={}",
                    jmsTemplate.isSessionTransacted(),
                    jmsTemplate.getSessionAcknowledgeMode(),
                    jmsTemplate.getReceiveTimeout()
            );

            logger.info("---- END JMS CONFIGURATION CHECK ----");
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

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSessionTransacted(listenerTransacted);
        return factory;
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setReceiveTimeout(templateReceiveTimeout);
        template.setSessionTransacted(templateTransacted);
        return template;
    }

}
