package com.example.artemis.service;

// import java.time.Duration;
// import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProducerService {

    private static final Logger logger = LoggerFactory.getLogger(ProducerService.class);

    private final JmsTemplate jmsTemplate;

    public ProducerService(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    // Synchronous send
    // blockOnAcknowledge = true
    public void send(String queueName, String message) {
        try {
            // LocalDateTime start = LocalDateTime.now();
            jmsTemplate.convertAndSend(queueName, message);
            // LocalDateTime end = LocalDateTime.now();
            // logger.info("Time lapsed: {} ms", Duration.between(start, end).toMillis());
            logger.info("SYNC message sent: {}", message);
        } catch (JmsException e) {
            logger.error("Failed to send sync message", e);
            throw e;
        }
    }
}
