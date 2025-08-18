package com.example.artemis.service;

import com.example.artemis.core.pool.ProducerPool;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.client.SendAcknowledgementHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CountDownLatch;

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

    public void sendSync(String queueName, String message) throws Exception {
        syncProducerPool.sendSync(queueName, message);
        logger.debug("Sent SYNC message to {}: {}", queueName, message);
    }

    public void sendAsync(String queueName, String message) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        asyncProducerPool.sendAsync(queueName, message, new SendAcknowledgementHandler() {
            @Override
            public void sendAcknowledged(Message msg) {
                latch.countDown();
                logger.debug("Async send acknowledged for queue {} message: {} latch: {}", 
                             queueName, msg, latch.getCount());
            }

            @Override
            public void sendFailed(Message msg, Exception e) {
                latch.countDown();
                logger.error("Async send FAILED for queue {} message: {} latch: {}. Exception: {}", 
                             queueName, msg, latch.getCount(), e.toString());
            }
        });

        // Optional: if you want blocking until ack
        // latch.await(10, TimeUnit.SECONDS);
    }
}
