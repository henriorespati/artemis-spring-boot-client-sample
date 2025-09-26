package com.example.artemis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProducerService {

    private static final Logger logger = LoggerFactory.getLogger(ProducerService.class);
    private final JmsTemplate jmsTemplate;

    public ProducerService(@Qualifier("syncJmsTemplate") JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    // Synchronous send 
    // blockOnAcknowledge = true 
    public void send(String queueName, String message) {

        // LocalDateTime now = LocalDateTime.now();
        try {
            jmsTemplate.convertAndSend(queueName, message);
            logger.info("SYNC message sent: {}", message);
        } catch (JmsException e) {
            logger.error("Failed to send sync message", e);
            throw e;
        }
        // LocalDateTime after = LocalDateTime.now();
        // logger.info("Time taken to send SYNC message: {} ms", java.time.Duration.between(now, after).toMillis());
    }

}
