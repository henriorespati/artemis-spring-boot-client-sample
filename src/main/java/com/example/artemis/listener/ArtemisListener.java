package com.example.artemis.listener;

import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import jakarta.jms.Destination;
import jakarta.jms.Message;

@Component
public class ArtemisListener 
    implements CommandLineRunner 
{

    private static final Logger logger = LoggerFactory.getLogger(ArtemisListener.class);
    private final JmsTemplate jmsTemplate;

    @Value("${app.queue.sync}")
    private String syncQueue;

    public ArtemisListener(
        JmsTemplate jmsTemplate
        ) {
        this.jmsTemplate = jmsTemplate;
    }

    /** Synchronous consumption */
    public void receiveSync() throws Exception {
        Message message = jmsTemplate.receive(syncQueue);
        if (message instanceof TextMessage textMessage) {
            logger.info("SYNC message received: {}", textMessage.getText());
        } 
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("Listening for SYNC messages on queue: {}", syncQueue);
        while (true) {
            try {
                receiveSync();
            } catch (Exception e) {
                logger.error("Error receiving SYNC message: {}", e.getMessage());
            }
            // Thread.sleep(1000); // Poll every second
        }
    }

    /** Asynchronous consumption */
    @JmsListener(destination = "${app.queue.async}")
    @Retryable(
        maxAttemptsExpression = "${spring.retry.max-attempts:2}", 
        backoff = @Backoff(
            delayExpression = "${spring.retry.delay:1000}", 
            multiplierExpression = "${spring.retry.multiplier:1.0}", 
            maxDelayExpression = "${spring.retry.max-delay:10000}"
        )
    )
    public void receiveAsync(TextMessage message) throws Exception {
        logger.info("ASYNC message received: {}", message.getText()); 
    }

    /** Transactional consumption */
    @JmsListener(destination = "${app.queue.transaction}")
    @Retryable(
        maxAttemptsExpression = "${spring.retry.max-attempts:2}", 
        backoff = @Backoff(
            delayExpression = "${spring.retry.delay:1000}", 
            multiplierExpression = "${spring.retry.multiplier:1.0}", 
            maxDelayExpression = "${spring.retry.max-delay:10000}"
        )
    )
    public void receiveTransaction(TextMessage message) throws Exception {
        try {
            String text = message.getText();
            logger.info("Received transactional message: {}", text);
        } catch (Exception e) {
            logger.error("Transaction rolled back for message: {}", message.getText(), e);
            throw e; // ensures Spring rolls back the session
        }
    }

    /** Request-Reply consumption */
    @JmsListener(destination = "${app.queue.request}")
    @Retryable(
        maxAttemptsExpression = "${spring.retry.max-attempts:2}", 
        backoff = @Backoff(
            delayExpression = "${spring.retry.delay:1000}", 
            multiplierExpression = "${spring.retry.multiplier:1.0}", 
            maxDelayExpression = "${spring.retry.max-delay:10000}"
        )
    )
    public void receiveAndReply(TextMessage message) throws Exception {
        String text = message.getText();
        logger.info("Received request: {}", text);

        Destination replyDest = message.getJMSReplyTo();
        if (replyDest != null) {
            String replyText = "Reply to: " + text;
            jmsTemplate.send(replyDest, session -> session.createTextMessage(replyText));
            logger.info("Sent reply: {} to queue {}", replyText, replyDest);
        } else {
            logger.warn("No JMSReplyTo set, cannot send reply for message: {}", text);
        }
    }
    
}
