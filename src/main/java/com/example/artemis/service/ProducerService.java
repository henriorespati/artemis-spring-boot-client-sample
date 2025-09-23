package com.example.artemis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ProducerService {

    private static final Logger logger = LoggerFactory.getLogger(ProducerService.class);
    private final JmsTemplate jmsTemplate;

    public ProducerService(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    // Asynchronous send 
    // blockOnAcknowledge=false must be set on the connection factory
    @Async
    public void sendAsync(String queueName, String message) {
        try {
            jmsTemplate.convertAndSend(queueName, message);
            logger.info("ASYNC send invoked for message: {}", message);
        } catch (Exception e) {
            logger.error("Failed to send ASYNC message: {}", message, e);
        }
    }
}
