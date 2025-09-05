package com.example.artemis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.artemis.jms.pool.ProducerPool;

@Service
public class ArtemisService {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisService.class);

    private final ProducerPool producerPool;

    public ArtemisService(ProducerPool producerPool) {
        this.producerPool = producerPool;
    }

    /**
     * Send a JMS message synchronously
     */
    public void sendSync(String queueName, String message) {
        try {
            producerPool.sendSync(queueName, message);
        } catch (Exception e) {
            logger.error("Failed to send SYNC JMS message to {}: {}", queueName, e.getMessage(), e);
        }
    }

    /**
     * Send a JMS message asynchronously
     */
    public void sendAsync(String queueName, String message) {
        try {
            producerPool.sendAsync(queueName, message);
        } catch (Exception e) {
            logger.error("Failed to send ASYNC JMS message to {}: {}", queueName, e.getMessage(), e);
        }
    }

    /**
     * Send a JMS message using request-reply pattern synchronously
     */
    public void sendRequestSync(String queueName, String message) {
        try {
            producerPool.sendAndReceiveSync(queueName, message, 5000);
        } catch (Exception e) {
            logger.error("Failed to send SYNC REQUEST JMS message to {}: {}", queueName, e.getMessage(), e);
        }
    }

    /**
     * Send a JMS message using request-reply pattern asynchronously
     * Callback will log the reply when it arrives
     */
    public void sendRequestAsync(String queueName, String message) {
        try {
            producerPool.sendAndReceiveAsync(queueName, message)
                .thenAccept(replyText -> {
                    logger.info("ASYNC reply message: {}", replyText);
                }).exceptionally(ex -> {
                    logger.error("Failed to receive ASYNC reply from {}: {}", queueName, ex.getMessage(), ex);
                    return null;
                });
        } catch (Exception e) {
            logger.error("Failed to send ASYNC REQUEST JMS message to {}: {}", queueName, e.getMessage(), e);
        }
    }
}
