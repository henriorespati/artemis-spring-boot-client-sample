package com.example.artemis.jms.pool;

import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ProducerPool {

    private static final Logger logger = LoggerFactory.getLogger(ProducerPool.class);

    private final ConnectionFactory connectionFactory;

    public ProducerPool(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
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
        try (JMSContext context = connectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE)) {
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
    public String sendAndReceiveSync(String queueName, String requestPayload, long timeoutMillis) {
        try (JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
            Queue requestQueue = context.createQueue(queueName);
            TemporaryQueue replyQueue = context.createTemporaryQueue();

            String correlationId = UUID.randomUUID().toString();

            TextMessage request = context.createTextMessage(requestPayload);
            request.setJMSReplyTo(replyQueue);
            request.setJMSCorrelationID(correlationId);

            context.createProducer().send(requestQueue, request);
            logger.debug("SYNC request sent to {} with correlationId={}", queueName, correlationId);

            JMSConsumer replyConsumer = context.createConsumer(replyQueue,
                    "JMSCorrelationID = '" + correlationId + "'");

            Message reply = replyConsumer.receive(timeoutMillis);
            if (reply instanceof TextMessage tm) {
                logger.debug("Received SYNC reply for correlationId={}: {}", correlationId, tm.getText());
                return tm.getText();
            } else if (reply != null) {
                return reply.toString();
            } else {
                logger.warn("No SYNC reply received for correlationId={} within {} ms", correlationId, timeoutMillis);
                return null;
            }
        } catch (JMSException | JMSRuntimeException e) {
            logger.error("Failed in sending SYNC request for queue {}", queueName, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Asynchronous request/reply with callback.
     */
    public CompletableFuture<String> sendAndReceiveAsync(String queueName, String requestPayload) {
        JMSContext context = connectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE);
        Queue requestQueue = context.createQueue(queueName);
        TemporaryQueue replyQueue = context.createTemporaryQueue();

        String correlationId = UUID.randomUUID().toString();
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            TextMessage request = context.createTextMessage(requestPayload);
            request.setJMSReplyTo(replyQueue);
            request.setJMSCorrelationID(correlationId);

            JMSConsumer replyConsumer = context.createConsumer(replyQueue,
                    "JMSCorrelationID = '" + correlationId + "'");
            replyConsumer.setMessageListener(reply -> {
                try {
                    String replyText = (reply instanceof TextMessage tm) ? tm.getText() : reply.toString();
                    logger.debug("Received ASYNC reply for correlationId={}: {}", correlationId, replyText);

                    future.complete(replyText); // Complete the future with the reply
                } catch (JMSException e) {
                    future.completeExceptionally(e);
                } finally {
                    try { replyConsumer.close(); } catch (Exception ignored) {}
                    try { context.close(); } catch (Exception ignored) {}
                }
            });

            context.createProducer().send(requestQueue, request);
            logger.debug("ASYNC request sent to {} with correlationId={}", queueName, correlationId);

        } catch (JMSException e) {
            logger.error("Failed to send ASYNC request to {}", queueName, e);
            future.completeExceptionally(e);
            try { context.close(); } catch (Exception ignored) {}
        }

        return future;
    }

    /**
     * Send using a transacted session (commit after single send).
     */
    public void sendTx(String queueName, String message) {
        try (JMSContext context = connectionFactory.createContext(JMSContext.SESSION_TRANSACTED)) {
            Queue queue = context.createQueue(queueName);

            context.createProducer().send(queue, message);

            // commit the transaction explicitly
            context.commit();

            logger.debug("Sent and committed TX message to {}: {}", queueName, message);
        } catch (JMSRuntimeException e) {
            logger.error("Failed to send TX message to {}", queueName, e);
            throw e;
        }
    }

    /**
     * Send using a transacted session (commit after batch sends).
     */
    public void sendTxBatch(String queueName, List<String> messages) {
        try (JMSContext context = connectionFactory.createContext(JMSContext.SESSION_TRANSACTED)) {
            Queue queue = context.createQueue(queueName);

            for (String message : messages) {
                context.createProducer().send(queue, message);
                logger.debug("Queued TX message to {}: {}", queueName, message);
            }

            // commit batch atomically
            context.commit();

            logger.info("Committed {} messages to {}", messages.size(), queueName);
        } catch (JMSRuntimeException e) {
            logger.error("Failed batch TX send to {}", queueName, e);
            throw e;
        }
    }
}
