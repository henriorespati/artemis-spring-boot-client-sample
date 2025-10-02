package com.example.artemis.service;

// import java.time.Duration;
// import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ProducerService {

    private static final Logger logger = LoggerFactory.getLogger(ProducerService.class);

    private final JmsTemplate jmsTemplate;
    private final RestTemplate restTemplate;

    // @Value("${app.consumer.sync-callback-url}")
    // private String syncConsumerCallbackUrl;

    public ProducerService(JmsTemplate jmsTemplate, RestTemplate restTemplate) {
        this.jmsTemplate = jmsTemplate;
        this.restTemplate = restTemplate;
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

            // Optional: Trigger the consumer REST API to process the batch immediately after sending
            // Only do this when using JmsTemplate receive() NOT JmsListener
            // restTemplate.postForObject(syncConsumerCallbackUrl, null, String.class);
        } catch (JmsException e) {
            logger.error("Failed to send sync message", e);
            throw e;
        }
    }
}
