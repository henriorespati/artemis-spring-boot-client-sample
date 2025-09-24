package com.example.artemis.config;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;

@Configuration
public class ArtemisJmsConfig {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisJmsConfig.class);

    @Autowired
    private DefaultJmsListenerContainerFactory jmsListenerContainerFactory;

    @Autowired
    private JmsTemplate jmsTemplate;

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

}
