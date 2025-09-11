package com.example.artemis.config;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Session;

import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.support.converter.SimpleMessageConverter;

@Configuration
public class ArtemisJmsConfig {

    @Value("${spring.artemis.user}")
    private String artemisUser;

    @Value("${spring.artemis.password}")
    private String artemisPassword;

    @Value("${spring.artemis.broker-url}")
    private String brokerUrl;

    private static final Logger logger = LoggerFactory.getLogger(ArtemisJmsConfig.class);

    @Bean
    CommandLineRunner check(ConnectionFactory cf) {
        return args -> {
            logger.info("JMS ConnectionFactory in use: {}", cf.getClass());
            if (cf instanceof JmsPoolConnectionFactory pool) {
                logger.info("Pool enabled, number of connections: {}, max connections: {}", 
                    pool.getNumConnections() , pool.getMaxConnections());
            }
        };
    }

    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrency("5-10"); // allow 5-10 concurrent consumers
        factory.setSessionTransacted(false); // non-transactional session
        return factory;
    }

    // Transactional listener factory
    @Bean
    public DefaultJmsListenerContainerFactory transactedJmsListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setSessionTransacted(true); // enable transaction
        factory.setSessionAcknowledgeMode(Session.CLIENT_ACKNOWLEDGE); // client ack mode for transaction
        factory.setConcurrency("1-1");
        return factory;
    }

    @Bean
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setMessageConverter(new SimpleMessageConverter());
        template.setSessionTransacted(false); // non-transactional session
        return template;
    }

    @Bean
    public JmsTemplate transactionalJmsTemplate(ConnectionFactory connectionFactory) {
        JmsTemplate template = new JmsTemplate(connectionFactory);
        template.setMessageConverter(new SimpleMessageConverter());
        template.setSessionTransacted(true); // Transacted session
        return template;
    }
}
