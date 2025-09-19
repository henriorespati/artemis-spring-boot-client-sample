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
    
    @Value("${spring.jms.listener.session.transacted}")
    private boolean listenerTransacted;

    @Value("${spring.jms.template.session.transacted}")
    private boolean templateTransacted;

    @Bean
    CommandLineRunner check(
            ConnectionFactory cf,
            JmsTemplate jmsTemplate,
            DefaultJmsListenerContainerFactory jmsListenerContainerFactory
    ) {
        return args -> {
            logger.info("---- JMS CONFIGURATION CHECK ----");

            // ConnectionFactory 
            logger.info("ConnectionFactory type: {}", cf.getClass().getName());
            if (cf instanceof JmsPoolConnectionFactory pool) {
                logger.info("Pool enabled: maxConnections={}, idleTimeout(ms)={}, blockIfFull={}, blockIfFullTimeout(ms)={}",
                        pool.getMaxConnections(),
                        pool.getConnectionIdleTimeout(),
                        pool.isBlockIfSessionPoolIsFull(),
                        pool.getBlockIfSessionPoolIsFullTimeout()
                );
                logger.info("NumConnections in use: {}", pool.getNumConnections());

                if (pool.getConnectionFactory() instanceof org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory amq) {
                    var locator = amq.getServerLocator();
                    logger.info("ActiveMQ Artemis connection settings:");
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
                    logger.info("  blockOnDurableSend={} blockOnAcknowledge={}",
                            locator.isBlockOnDurableSend(),
                            locator.isBlockOnAcknowledge()
                    );
                }
            }

            // JmsTemplate
            logger.info("JmsTemplate: sessionTransacted={}, acknowledgeMode={}",
                    jmsTemplate.isSessionTransacted(),
                    jmsTemplate.getSessionAcknowledgeMode()
            );

            logger.info("---- END JMS CONFIGURATION CHECK ----");
        };
    }

    @Bean
    public ActiveMQConnectionFactory activeMQConnectionFactory() {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        factory.setUser(artemisUser);
        factory.setPassword(artemisPassword);
        return factory;
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSessionTransacted(listenerTransacted);
        logger.info("connectionFactoryClass={}",connectionFactory.getClass().getName());
        return factory;
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setSessionTransacted(templateTransacted);
        return template;
    }

}
