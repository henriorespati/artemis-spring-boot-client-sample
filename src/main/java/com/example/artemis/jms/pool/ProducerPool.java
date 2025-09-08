package com.example.artemis.jms.pool;

import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

}
