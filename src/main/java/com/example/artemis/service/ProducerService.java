package com.example.artemis.service;

import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;

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
    public String sendRequest(String requestQueueName, String message) {
        return jmsTemplate.execute(session -> {
            MessageProducer producer = session.createProducer(session.createQueue(requestQueueName));
            Queue replyQueue = session.createTemporaryQueue();
            TextMessage msg = session.createTextMessage(message);
            msg.setJMSReplyTo(replyQueue);
            producer.send(msg);

            MessageConsumer consumer = session.createConsumer(replyQueue);
            Message reply = consumer.receive(receiveTimeout);

            if (reply != null) {
                String replyText = ((TextMessage) reply).getText();
                logger.info("Request message sent: '{}', received message: '{}'", message, replyText);
                return replyText;
            } else {
                logger.warn("Request message sent: '{}', but no reply received after timeout", message);
                return null;
            }
        }, true); 
    }
}
