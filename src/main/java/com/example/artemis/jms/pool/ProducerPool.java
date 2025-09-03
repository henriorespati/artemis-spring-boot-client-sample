package com.example.artemis.jms.pool;

import com.example.artemis.config.ArtemisProperties;
import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProducerPool {

    private static final Logger logger = LoggerFactory.getLogger(ProducerPool.class);

    private final ConnectionFactory cf;
    private final ArtemisProperties props;

    public ProducerPool(ConnectionFactory cf, ArtemisProperties props) {
        this.cf = cf;
        this.props = props;
    }

    public void sendSync(String queueName, String message) {
        try (JMSContext context = cf.createContext(
                props.getUser(),
                props.getPassword(),
                JMSContext.AUTO_ACKNOWLEDGE)) {

            Queue queue = context.createQueue(queueName);
            JMSProducer producer = context.createProducer();
            producer.send(queue, message);

            logger.debug("Sent SYNC message to queue {}: {}", queueName, message);

        } catch (JMSRuntimeException e) {
            logger.error("Failed to send SYNC message to queue {}", queueName, e);
            throw e; // rethrow so caller can handle
        }
    }

    public void sendAsync(String queueName, String message) {
        try (JMSContext context = cf.createContext(
                props.getUser(),
                props.getPassword(),
                JMSContext.AUTO_ACKNOWLEDGE)) {

            Queue queue = context.createQueue(queueName);
            JMSProducer producer = context.createProducer();

            producer.setAsync(new CompletionListener() {
                @Override
                public void onCompletion(Message msg) {
                    try {
                        logger.debug("ASYNC send complete for queue {} (JMSMessageID={})",
                                queueName, msg.getJMSMessageID());
                    } catch (JMSException e) {
                        logger.warn("ASYNC send complete but failed to read message ID", e);
                    }
                }

                @Override
                public void onException(Message msg, Exception exception) {
                    logger.error("ASYNC send failed for queue {}", queueName, exception);
                }
            });

            logger.debug("Sending ASYNC message to queue {}: {}", queueName, message);
            producer.send(queue, message);

        } catch (JMSRuntimeException e) {
            logger.error("Failed to send ASYNC message to queue {}", queueName, e);
            throw e;
        }
    }
}
