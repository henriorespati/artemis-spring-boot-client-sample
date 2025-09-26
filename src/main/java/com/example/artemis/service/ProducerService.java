package com.example.artemis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.jms.TextMessage;

import java.util.List;
import java.util.UUID;

@Service
public class ProducerService {

    private static final Logger logger = LoggerFactory.getLogger(ProducerService.class);

    @Value("${app.consumer.callback-url}")
    private String consumerCallbackUrl;

    private final JmsTemplate jmsTemplate;
    private final RestTemplate restTemplate;

    public ProducerService(JmsTemplate jmsTemplate, RestTemplate restTemplate) {
        this.jmsTemplate = jmsTemplate;
        this.restTemplate = restTemplate;
    }

    // Transactional send 
    // Session transacted = true
    public void sendTransaction(String queueName, List<String> messages) {
        String batchId = UUID.randomUUID().toString();
        int batchSize = messages.size();

        try {
            jmsTemplate.execute(session -> {
                var producer = session.createProducer(session.createQueue(queueName));
                for (String msg : messages) {
                    TextMessage msgText = session.createTextMessage(msg);
                    msgText.setStringProperty("batchId", batchId);
                    msgText.setIntProperty("batchSize", batchSize);
                    producer.send(msgText);
                    logger.info("Transactional message sent: {}", msg);
                }
                
                // Commit the transaction
                session.commit();
                logger.info("Transaction {} sent and committed with {} messages", batchId, batchSize);

                // Optional: Trigger the consumer REST API to process the batch immediately after sending
                // Error sent by the consumer will be caught here and trigger rollback
                restTemplate.postForObject(consumerCallbackUrl, batchId, String.class);

                return null;
            }, true); 
        } catch (Exception e) {
            logger.error("Transaction {} rolled back in Producer", batchId);
            logger.debug(e.toString());
            throw e;
        }
    }

}
