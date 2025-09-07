package com.example.artemis.jms.pool;

import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ProducerPool {

    private static final Logger logger = LoggerFactory.getLogger(ProducerPool.class);

    private final ConnectionFactory connectionFactory;

    public ProducerPool(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
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
