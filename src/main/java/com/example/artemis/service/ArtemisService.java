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
     * Send a JMS message using a transacted session (commit after single send)
     */
    public void sendTx(String queueName, String message) {
        try {
            producerPool.sendTx(queueName, message);
        } catch (Exception e) {
            logger.error("Failed to send TX JMS message to {}: {}", queueName, e.getMessage(), e);
        }
    }

    /**
     * Send a batch of JMS messages using a transacted session (commit after batch sends)
     */
    public void sendTxBatch(String queueName, java.util.List<String> messages) {
        try {
            producerPool.sendTxBatch(queueName, messages);
        } catch (Exception e) {
            logger.error("Failed to send TX BATCH JMS messages to {}: {}", queueName, e.getMessage(), e);
        }
    }
}
