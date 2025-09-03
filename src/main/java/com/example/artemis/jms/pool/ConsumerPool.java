package com.example.artemis.jms.pool;

import com.example.artemis.config.ArtemisProperties;
import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ConsumerPool {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerPool.class);

    private final ConnectionFactory cf;
    private final ArtemisProperties props;
    private final List<JMSContext> contexts = new ArrayList<>();
    private final List<Thread> syncThreads = new ArrayList<>();

    public ConsumerPool(ConnectionFactory cf, ArtemisProperties props) {
        this.cf = cf;
        this.props = props;
    }

    // Called by Spring initMethod
    public void start() {
        int threads = props.getConsumerThreadsPerQueue();
        boolean isAsync = !"SYNC".equalsIgnoreCase(props.getConsumerMode());

        for (String queueName : props.getQueues()) {
            for (int i = 0; i < threads; i++) {
                if (isAsync) {
                    startAsyncConsumer(queueName);
                } else {
                    startSyncConsumer(queueName);
                }
            }
        }

        logger.info("ConsumerPool started {} listeners for {} queues", threads, props.getQueues().size());
    }

    private void startAsyncConsumer(String queueName) {
        JMSContext context = cf.createContext(
                props.getUser(),
                props.getPassword(),
                JMSContext.AUTO_ACKNOWLEDGE
        );

        Queue queue = context.createQueue(queueName);
        JMSConsumer consumer = context.createConsumer(queue);

        consumer.setMessageListener(msg -> {
            try {
                if (msg instanceof TextMessage) {
                    String body = ((TextMessage) msg).getText();
                    logger.info("ASYNC received message on queue {}: {}", queueName, body);
                } else {
                    logger.warn("ASYNC received non-text message on queue {}: {}", queueName, msg);
                }
            } catch (Exception e) {
                logger.error("Error processing ASYNC message on queue {}", queueName, e);
            }
        });

        contexts.add(context);
        logger.info("Started ASYNC consumer for queue {}", queueName);
    }

    private void startSyncConsumer(String queueName) {
        JMSContext context = cf.createContext(
                props.getUser(),
                props.getPassword(),
                JMSContext.AUTO_ACKNOWLEDGE
        );

        Queue queue = context.createQueue(queueName);
        JMSConsumer consumer = context.createConsumer(queue);

        Thread t = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Message msg = consumer.receive(5000); // timeout in ms
                        if (msg != null) {
                            if (msg instanceof TextMessage) {
                                String body = ((TextMessage) msg).getText();
                                logger.info("SYNC received message on queue {}: {}", queueName, body);
                                throw new RuntimeException("Simulated processing error");
                            } else {
                                logger.warn("SYNC received non-text message on queue {}: {}", queueName, msg);
                            }
                        }
                    } catch (JMSException e) {
                        logger.error("Error receiving SYNC message on queue {}", queueName, e);
                    } catch (Exception e) {
                        // Handle other exceptions
                    }
                }
            } finally {
                try {
                    consumer.close();
                } catch (Exception e) {
                    logger.warn("Error closing consumer for queue {}", queueName, e);
                }
            }
        }, "SyncConsumer-" + queueName);

        t.start();
        contexts.add(context);
        syncThreads.add(t);

        logger.info("Started SYNC consumer thread for queue {}", queueName);
    }

    // Called by Spring destroyMethod
    public void stop() {
        syncThreads.forEach(Thread::interrupt);
        syncThreads.clear();

        for (JMSContext context : contexts) {
            try {
                context.close();
            } catch (Exception e) {
                logger.warn("Error closing JMSContext", e);
            }
        }
        contexts.clear();
        logger.info("ConsumerPool stopped all listeners");
    }
}
