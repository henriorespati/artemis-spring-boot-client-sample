package com.example.artemis.config;

import java.util.Map;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
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

    // Default Pooled ConnectionFactory
    @Bean
    public JmsPoolConnectionFactory defaultPooledConnectionFactory() {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        factory.setUser(artemisUser);
        factory.setPassword(artemisPassword);

        JmsPoolConnectionFactory pool = new JmsPoolConnectionFactory();
        pool.setConnectionFactory(factory);
        pool.setMaxConnections(poolMaxConnections);
        pool.setMaxSessionsPerConnection(poolMaxSessionsPerConnection);
        return pool;
    }

    // Sync Pooled ConnectionFactory
    @Bean
    public JmsPoolConnectionFactory syncPooledConnectionFactory() {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        factory.setUser(artemisUser);
        factory.setPassword(artemisPassword);
        factory.setBlockOnAcknowledge(true); // for SYNC receive

        JmsPoolConnectionFactory pool = new JmsPoolConnectionFactory();
        pool.setConnectionFactory(factory);
        pool.setMaxConnections(poolMaxConnections);
        pool.setMaxSessionsPerConnection(poolMaxSessionsPerConnection);
        
        return pool;
    }

    // Sync listener container factory
    @Bean
    public DefaultJmsListenerContainerFactory syncJmsListenerContainerFactory(
            @Qualifier("syncPooledConnectionFactory") JmsPoolConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE); // for SYNC listener
        factory.setConcurrency(listenerMinConcurrency + "-" + listenerMaxConcurrency);
        return factory;
    }

    // Default jms template
    @Bean
    public JmsTemplate defaultJmsTemplate(
            @Qualifier("defaultPooledConnectionFactory") JmsPoolConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setReceiveTimeout(templateReceiveTimeout);
        return template;
    }

    // JMS configuration check on debug level
    @Bean
    public CommandLineRunner check(
            // Map<String, JmsPoolConnectionFactory> pools,
            Map<String, CachingConnectionFactory> pools,
            Map<String, JmsTemplate> templates,
            Map<String, DefaultJmsListenerContainerFactory> listeners
    ) {
        return args -> {
            logger.debug("---- JMS CONFIGURATION CHECK ----");

            pools.forEach((poolName, pool) -> {
                logger.debug("JmsPoolConnectionFactory bean: {}", poolName);

                templates.forEach((templateName, jmsTemplate) -> {
                    logger.debug("Associated JmsTemplate bean: {}", templateName);

                    try {
                        jmsTemplate.execute(session -> {
                            logger.debug("Forcing connection creation for diagnostics...");
                            return null;
                        });
                    } catch (Exception e) {
                        logger.warn("Dummy connection attempt failed: {}", e.getMessage());
                    }

                    logger.debug("ConnectionFactory type: {}", pool.getClass().getName());

                    logger.debug("ConnectionFactory settings:");
                    // logger.debug("  maxConnections={} maxSessionsPerConnection={} blockIfFull={} blockIfFullTimeout(ms)={}",
                    //         pool.getMaxConnections(),
                    //         pool.getMaxSessionsPerConnection(),
                    //         pool.getSessionCacheSize(),
                    //         pool.isBlockIfSessionPoolIsFull(),
                    //         pool.getBlockIfSessionPoolIsFullTimeout()
                    // );
                    logger.debug("  cachedSessionCount={} sessionCacheSize={}",
                            pool.getCachedSessionCount(),
                            pool.getSessionCacheSize()
                    );
                    // logger.debug("  connectionIdleTimeout(ms)={} connectionCheckInterval(ms)={} useProviderJMSContext={}",
                    //         pool.getConnectionIdleTimeout(),
                    //         pool.getConnectionCheckInterval(),
                    //         pool.isUseProviderJMSContext()
                    // );
                    // logger.debug("  numConnectionsInUse={}", pool.getNumConnections());

                    // var delegate = pool.getConnectionFactory();
                    var delegate = pool.getTargetConnectionFactory();
                    logger.debug("Delegate factory type: {}", delegate.getClass().getName());

                    if (delegate instanceof ActiveMQConnectionFactory amq) {
                        var locator = amq.getServerLocator();

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

                    logger.debug("JmsTemplate settings:");
                    logger.debug("  sessionTransacted={} acknowledgeMode={} receiveTimeout(ms)={} timeToLive(ms)={}",
                            jmsTemplate.isSessionTransacted(),
                            jmsTemplate.getSessionAcknowledgeMode(),
                            jmsTemplate.getReceiveTimeout(),
                            jmsTemplate.getTimeToLive()
                    );
                });

                listeners.forEach((listenerName, listener) -> {
                    logger.debug("Listener container factory bean: {}", listenerName);
                });
            });

            logger.debug("---- END JMS CONFIGURATION CHECK ----");
        };
    }

}
