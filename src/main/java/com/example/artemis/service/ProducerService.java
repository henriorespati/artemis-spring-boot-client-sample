package com.example.artemis.service;

import jakarta.jms.CompletionListener;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.JMSProducer;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;

import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ProducerService {

    private static final Logger logger = LoggerFactory.getLogger(ProducerService.class);

    private final JmsPoolConnectionFactory connectionFactory;
    private final JmsTemplate jmsTemplate;

    public ProducerService(
        JmsTemplate jmsTemplate
        , JmsPoolConnectionFactory connectionFactory
    ) {
        this.jmsTemplate = jmsTemplate;
        this.connectionFactory = connectionFactory;
    }

    private final int timeoutMs = 5000;

    /** Synchronous send */
    @Retryable(
        maxAttemptsExpression = "${spring.retry.max-attempts:2}", 
        backoff = @Backoff(
            delayExpression = "${spring.retry.delay:1000}", 
            multiplierExpression = "${spring.retry.multiplier:1.0}", 
            maxDelayExpression = "${spring.retry.max-delay:10000}"
        )
    )
    public void send(String queueName, String message) throws JmsException {
        // JmsTemplate jmsTemplate = getRandomTemplate();
        jmsTemplate.convertAndSend(queueName, message);
        logger.info("SYNC message sent: {}", message);
    }

    /** Asynchronous send */
    @Retryable(
        maxAttemptsExpression = "${spring.retry.max-attempts:2}", 
        backoff = @Backoff(
            delayExpression = "${spring.retry.delay:1000}", 
            multiplierExpression = "${spring.retry.multiplier:1.0}", 
            maxDelayExpression = "${spring.retry.max-delay:10000}"
        )
    )
    public CompletableFuture<Void> sendAsync(String queueName, String message) {
        return CompletableFuture.runAsync(() -> {
            try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
                Queue queue = context.createQueue(queueName);
                JMSProducer producer = context.createProducer();

                producer.setAsync(new CompletionListener() {
                    @Override
                    public void onCompletion(Message msg) {
                        try {
                            logger.info("ASYNC send complete for {} (JMSMessageID={})",
                                    queueName, msg.getJMSMessageID());
                        } catch (JMSException e) {
                            logger.warn("Failed to read JMSMessageID for async send", e);
                        }
                    }

                    @Override
                    public void onException(Message msg, Exception exception) {
                        logger.error("ASYNC send failed for {}", queueName, exception);
                    }
                });

                producer.send(queue, message);
                logger.info("ASYNC send invoked for message: {}", message);
            } catch (Exception e) {
                logger.error("Failed to send async message", e);
            }
        });
    }

    /** Transactional send */
    // Caveat: the retry will re-send all messages in the transaction, so the consumer must be idempotent
    @Retryable(
        maxAttemptsExpression = "${spring.retry.max-attempts:2}", 
        backoff = @Backoff(
            delayExpression = "${spring.retry.delay:1000}", 
            multiplierExpression = "${spring.retry.multiplier:1.0}", 
            maxDelayExpression = "${spring.retry.max-delay:10000}"
        )
    )
    public void sendTransaction(String queueName, List<String> messages) {
        jmsTemplate.execute(session -> {
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
    // Caveat: the retry will re-send the request message, so the consumer must be idempotent
    @Retryable(
        maxAttemptsExpression = "${spring.retry.max-attempts:2}", 
        backoff = @Backoff(
            delayExpression = "${spring.retry.delay:1000}", 
            multiplierExpression = "${spring.retry.multiplier:1.0}", 
            maxDelayExpression = "${spring.retry.max-delay:10000}"
        )
    )
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
