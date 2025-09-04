package com.example.artemis.jms.pool;

import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class ProducerPool {

    private static final Logger logger = LoggerFactory.getLogger(ProducerPool.class);

    private final ConnectionFactory connectionFactory;
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
}
