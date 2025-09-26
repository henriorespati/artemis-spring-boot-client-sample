package com.example.artemis.service;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProducerService {

    private static final Logger logger = LoggerFactory.getLogger(ProducerService.class);

    @Value("${spring.jms.template.receive-timeout}")
    private int receiveTimeout;

    private final JmsTemplate jmsTemplate;

    public ProducerService(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    // Send Request and wait for Reply  
    // Use durable queue for reply
    public String sendRequest(String requestQueueName, String replyQueueName, String message) {
        // Generate a unique correlation ID for this request
        String correlationId = UUID.randomUUID().toString();

        // Set a custom reply selector so we only receive our message
        String selector = "JMSCorrelationID = '" + correlationId + "'";

        try {
            // Send the request message with the correlation ID and reply queue
            jmsTemplate.send(requestQueueName, session -> {
                TextMessage msg = session.createTextMessage(message);
                msg.setJMSReplyTo(session.createQueue(replyQueueName));
                msg.setJMSCorrelationID(correlationId);
                return msg;
            });

            Message reply = jmsTemplate.receiveSelected(replyQueueName, selector);

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
