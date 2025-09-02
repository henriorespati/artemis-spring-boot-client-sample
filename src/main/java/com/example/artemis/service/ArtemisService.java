package com.example.artemis.service;

import jakarta.jms.JMSException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.example.artemis.jms.pool.ProducerPool;

@Service
public class ArtemisService {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisService.class);

    private final ProducerPool syncProducerPool;
    private final ProducerPool asyncProducerPool;

    public ArtemisService(@Qualifier("syncProducerPool") ProducerPool syncProducerPool,
                          @Qualifier("asyncProducerPool") ProducerPool asyncProducerPool) {
        this.syncProducerPool = syncProducerPool;
        this.asyncProducerPool = asyncProducerPool;
    }

    public void sendSync(String queueName, String message) throws JMSException {
        try {
            syncProducerPool.sendSync(queueName, message);
        } catch (Exception e) {
            logger.error("Failed to send SYNC JMS message to {}: {}", queueName, e.getMessage());
        }
        logger.debug("Sent SYNC JMS message to {}: {}", queueName, message);
    }

    public void sendAsync(String queueName, String message) throws JMSException {
        try {
            asyncProducerPool.sendAsync(queueName, message);
        } catch (Exception e) {
            logger.error("Failed to send ASYNC JMS message to {}: {}", queueName, e.getMessage());
        }
        logger.debug("Sent ASYNC JMS message to {}: {}", queueName, message);
    }
}
