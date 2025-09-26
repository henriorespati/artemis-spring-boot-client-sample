package com.example.artemis.service;

import jakarta.annotation.PostConstruct;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProducerService {

    private static final Logger logger = LoggerFactory.getLogger(ProducerService.class);

    @Value("${spring.jms.template.receive-timeout}")
    private int receiveTimeout;

    @Value("${app.consumer.callback-url}")
    private String consumerCallbackUrl;

    private final JmsTemplate defaultJmsTemplate;
    private final JmsTemplate syncJmsTemplate;
    private final JmsTemplate txJmsTemplate;
    private final RestTemplate restTemplate;

    public ProducerService(
            @Qualifier("defaultJmsTemplate") JmsTemplate defaultJmsTemplate,
            @Qualifier("syncJmsTemplate") JmsTemplate syncJmsTemplate,
            @Qualifier("txJmsTemplate") JmsTemplate txJmsTemplate,
            RestTemplate restTemplate) {
        this.defaultJmsTemplate = defaultJmsTemplate;
        this.syncJmsTemplate = syncJmsTemplate;
        this.txJmsTemplate = txJmsTemplate;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() {
        logger.info("ProducerService bean class = {}", this.getClass().getName());
    }

    /** Scenario 1: Synchronous send */
    // blockOnAcknowledge = true
    public void send(String queueName, String message) {

        // LocalDateTime now = LocalDateTime.now();
        try {
            syncJmsTemplate.convertAndSend(queueName, message);
            logger.info("SYNC message sent: {}", message);
        } catch (JmsException e) {
            logger.error("Failed to send sync message", e);
            throw e;
        }
        // LocalDateTime after = LocalDateTime.now();
        // logger.info("Time taken to send SYNC message: {} ms", java.time.Duration.between(now, after).toMillis());
    }

    /** Scenario 2: Transactional send */
    // Session transacted = true
    public void sendTransaction(String queueName, List<String> messages) {
        String batchId = UUID.randomUUID().toString();
        int batchSize = messages.size();

        try {
            txJmsTemplate.execute(session -> {
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
                restTemplate.postForObject(consumerCallbackUrl, batchId, String.class);

                return null;
            }, true); 
        } catch (Exception e) {
            logger.error("Transaction rolled back", e);
            throw e;
        }
    }

    /** Scenario 3: Asynchronous send */
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

    /** Scenario 4: Request/Reply send */
    // Use durable queue for reply
    public String sendRequest(String requestQueueName, String replyQueueName, String message) {
        // Generate a unique correlation ID for this request
        String correlationId = UUID.randomUUID().toString();

        // Set a custom reply selector so we only receive our message
        String selector = "JMSCorrelationID = '" + correlationId + "'";

        try {
            // Send the request message with the correlation ID and reply queue
            defaultJmsTemplate.send(requestQueueName, session -> {
                TextMessage msg = session.createTextMessage(message);
                msg.setJMSReplyTo(session.createQueue(replyQueueName));
                msg.setJMSCorrelationID(correlationId);
                return msg;
            });

            Message reply = defaultJmsTemplate.receiveSelected(replyQueueName, selector);

            if (reply != null) {
                String replyText = ((TextMessage) reply).getText();
                logger.info("Request message sent: '{}', received message: '{}', correlationId: {}", 
                    message, replyText, correlationId);
                return replyText;
            } else {
                logger.warn("Request message sent: '{}', but no reply received", message);
                return null;
            }
        } catch (JMSException e) {
            logger.error("Failed to send or receive JMS message", e);
            return null;
        }
    }
}
