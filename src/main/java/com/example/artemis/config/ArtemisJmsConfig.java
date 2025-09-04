package com.example.artemis.config;

import com.example.artemis.jms.pool.ProducerPool;
import jakarta.jms.ConnectionFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArtemisJmsConfig {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisJmsConfig.class);

    private final ArtemisProperties appProps;

    public ArtemisJmsConfig(ArtemisProperties appProps) {
        this.appProps = appProps;
    }

    @Bean
    public ProducerPool producerPool(ConnectionFactory connectionFactory) {
        logger.info("Creating ProducerPool for queues {}", appProps.getQueues());
        return new ProducerPool(connectionFactory, appProps.getQueues());
    }

}
