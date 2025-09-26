package com.example.artemis.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ProducerService {

    private static final Logger logger = LoggerFactory.getLogger(ProducerService.class);

    private final JmsTemplate defaultJmsTemplate;

    public ProducerService(
            @Qualifier("defaultJmsTemplate") JmsTemplate defaultJmsTemplate) {
        this.defaultJmsTemplate = defaultJmsTemplate;
    }

    @PostConstruct
    public void init() {
        logger.info("ProducerService bean class = {}", this.getClass().getName());
    }

    // Asynchronous send 
    // blockOnAcknowledge = false 
    @Async
    public void sendAsync(String queueName, String message) {
        try {
            defaultJmsTemplate.convertAndSend(queueName, message);
            logger.info("ASYNC send invoked for message: {}", message);
        } catch (Exception e) {
            logger.error("Failed to send ASYNC message: {}", message, e);
        }
    }

}
