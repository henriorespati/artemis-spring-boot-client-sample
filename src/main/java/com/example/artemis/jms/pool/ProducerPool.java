package com.example.artemis.jms.pool;

import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ProducerPool {

    private static final Logger logger = LoggerFactory.getLogger(ProducerPool.class);

    private final ConnectionFactory connectionFactory;
    private final Map<String, Consumer<String>> callbacks = new ConcurrentHashMap<>();
    private final List<String> queues;

    public ProducerPool(ConnectionFactory connectionFactory, List<String> queues) {
        this.connectionFactory = connectionFactory;
        this.queues = queues;
    }

    public void sendSync(String queueName, String message) {
        try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
            Queue queue = context.createQueue(queueName);
            context.createProducer().send(queue, message);
            logger.debug("Sent SYNC message to {}: {}", queueName, message);
        } catch (JMSRuntimeException e) {
            logger.error("Failed to send SYNC message to {}", queueName, e);
            throw e;
        }
    }

    public void sendAsync(String queueName, String message) {
        try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
            Queue queue = context.createQueue(queueName);
            JMSProducer producer = context.createProducer();
            producer.setAsync(new CompletionListener() {
                @Override
                public void onCompletion(Message msg) {
                    try {
                        logger.debug("ASYNC send complete for {} (JMSMessageID={})",
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
            logger.debug("Sending ASYNC message to {}: {}", queueName, message);
        } catch (JMSRuntimeException e) {
            logger.error("Failed to send ASYNC message to {}", queueName, e);
            throw e;
        }
    }

    /**
     * Synchronous request/reply.
     */
    public String sendAndReceive(String queueName, String requestPayload, long timeoutMillis) {
        try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
            Queue requestQueue = context.createQueue(queueName);
            TemporaryQueue replyQueue = context.createTemporaryQueue();

            String correlationId = UUID.randomUUID().toString();

            TextMessage request = context.createTextMessage(requestPayload);
            request.setJMSReplyTo(replyQueue);
            request.setJMSCorrelationID(correlationId);

            logger.debug("Sending request to {} with correlationId={}", queueName, correlationId);
            context.createProducer().send(requestQueue, request);

            JMSConsumer replyConsumer = context.createConsumer(replyQueue,
                    "JMSCorrelationID = '" + correlationId + "'");

            Message reply = replyConsumer.receive(timeoutMillis);
            if (reply instanceof TextMessage tm) {
                logger.debug("Received reply for correlationId={}: {}", correlationId, tm.getText());
                return tm.getText();
            } else if (reply != null) {
                return reply.toString();
            } else {
                logger.warn("No reply received for correlationId={} within {} ms", correlationId, timeoutMillis);
                return null;
            }
        } catch (JMSException | JMSRuntimeException e) {
            logger.error("Failed in request/reply for queue {}", queueName, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Asynchronous request/reply with callback.
     */
    public void sendAndReceiveAsync(String queueName, String requestPayload, Consumer<String> onReply) {
        JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
        Queue requestQueue = context.createQueue(queueName);
        TemporaryQueue replyQueue = context.createTemporaryQueue();

        String correlationId = UUID.randomUUID().toString();

        try {
            TextMessage request = context.createTextMessage(requestPayload);
            request.setJMSReplyTo(replyQueue);
            request.setJMSCorrelationID(correlationId);

            callbacks.put(correlationId, onReply);

            JMSConsumer replyConsumer = context.createConsumer(replyQueue,
                    "JMSCorrelationID = '" + correlationId + "'");
            replyConsumer.setMessageListener(reply -> {
                try {
                    String responseText = (reply instanceof TextMessage tm) ? tm.getText() : reply.toString();
                    logger.debug("Async reply received for correlationId={}: {}", correlationId, responseText);

                    Consumer<String> callback = callbacks.remove(correlationId);
                    if (callback != null) {
                        callback.accept(responseText);
                    }
                } catch (JMSException e) {
                    logger.error("Error reading async reply", e);
                } finally {
                    try { replyConsumer.close(); } catch (Exception ignored) {}
                    try { context.close(); } catch (Exception ignored) {}
                }
            });

            context.createProducer().send(requestQueue, request);
            logger.debug("Async request sent to {} with correlationId={}", queueName, correlationId);

        } catch (JMSException e) {
            logger.error("Failed to send async request to {}", queueName, e);
            callbacks.remove(correlationId);
            try { context.close(); } catch (Exception ignored) {}
            throw new RuntimeException(e);
        }
    }
}
