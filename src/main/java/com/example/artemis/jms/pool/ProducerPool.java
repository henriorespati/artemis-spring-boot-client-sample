package com.example.artemis.jms.pool;

import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ProducerPool {

    private static final Logger logger = LoggerFactory.getLogger(ProducerPool.class);

    private final ConnectionFactory connectionFactory;

    public ProducerPool(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
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

}
