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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class ProducerService {

    private static final Logger logger = LoggerFactory.getLogger(ProducerService.class);

    @Value("${spring.jms.template.receive-timeout}")
    private int receiveTimeout;

    private final JmsPoolConnectionFactory connectionFactory;
    private final JmsTemplate jmsTemplate;

    public ProducerService(JmsTemplate jmsTemplate, JmsPoolConnectionFactory connectionFactory) {
        this.jmsTemplate = jmsTemplate;
        this.connectionFactory = connectionFactory;
    }

    /** Scenario 1: Synchronous send */
    public void send(String queueName, String message) {
        try {
            jmsTemplate.convertAndSend(queueName, message);
            logger.info("SYNC message sent: {}", message);
        } catch (JmsException e) {
            logger.error("Failed to send sync message", e);
            throw e;
        }
    }

    /** Scenario 2: Transactional send */
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

    /** Scenario 3: Asynchronous send */
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

    /** Scenario 4: Request/Reply send */
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
