package com.example.artemis.service;

import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProducerService {

    private static final Logger logger = LoggerFactory.getLogger(ProducerService.class);
    private final JmsTemplate jmsTemplate;
    private final JmsTemplate transactionalJmsTemplate;
    private final int timeoutMs = 5000;

    // private int retry = 0;

    public ProducerService(@Qualifier("jmsTemplate") JmsTemplate jmsTemplate,
            @Qualifier("transactionalJmsTemplate") JmsTemplate transactionalJmsTemplate) {
        this.jmsTemplate = jmsTemplate;
        this.transactionalJmsTemplate = transactionalJmsTemplate;
    }

    /** Synchronous send */
    @Retryable(maxAttempts = 2, backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
    public void send(String queueName, String message) {
        // if (Math.random() < 0.5) {
        //     retry++;
        //     logger.warn("Exception occurred, will retry: attempt: {}", retry);
        //     if(retry >= 2) retry = 0; // reset retry counter after max attempts
        //     // Simulate transient error
        //     throw new RuntimeException("Simulated transient error");
        // }
        // retry = 0; // reset retry counter on success

        jmsTemplate.convertAndSend(queueName, message);
        logger.info("SYNC message sent: {}", message);
    }

    /** Transactional send */
    @Retryable(maxAttempts = 2, backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
    public void sendTransaction(String queueName, List<String> messages) {
        transactionalJmsTemplate.execute(session -> {
            var producer = session.createProducer(session.createQueue(queueName));
            for (String msg : messages) {
                producer.send(session.createTextMessage(msg));
                logger.info("Transactional message sent: {}", msg);
            }
            // Commit the transaction
            session.commit();
            logger.info("Transaction committed with {} messages", messages.size());
            return null;
        }, true); // sessionTransacted=true
    }

    /** Request/Reply send */
    @Retryable(maxAttempts = 2, backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 10000))
    public String sendRequest(String requestQueueName, String message) {
        return jmsTemplate.execute(session -> {
            MessageProducer producer = session.createProducer(session.createQueue(requestQueueName));
            Queue replyQueue = session.createTemporaryQueue();
            TextMessage msg = session.createTextMessage(message);
            msg.setJMSReplyTo(replyQueue);
            producer.send(msg);

            MessageConsumer consumer = session.createConsumer(replyQueue);
            Message reply = consumer.receive(timeoutMs);

            if (reply != null) {
                String replyText = ((TextMessage) reply).getText();
                logger.info("Request message sent: '{}', received message: '{}'", message, replyText);
                return replyText;
            } else {
                logger.warn("Request message sent: '{}', but no reply received after timeout", message);
                return null;
            }
        }, true); // sessionTransacted=true
    }
}
