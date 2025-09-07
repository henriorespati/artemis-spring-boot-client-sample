package com.example.artemis.jms.pool;

import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

public class ConsumerPool {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerPool.class);

    private final ConnectionFactory connectionFactory;
    private final List<JMSContext> contexts = new ArrayList<>();
    private final List<Thread> txThreads = new ArrayList<>();
    private final List<String> queues;

    private final int threadsPerQueue;

    public ConsumerPool(ConnectionFactory connectionFactory, List<String> queues,
                        int threadsPerQueue) {
        this.connectionFactory = connectionFactory;
        this.queues = queues;
        this.threadsPerQueue = threadsPerQueue;
    }

    public void start() {
        for (String queueName : queues) {
            for (int i = 0; i < threadsPerQueue; i++) {
                startTxConsumer(queueName);                
            }
        }
        logger.info("ConsumerPool started {} threads per queue for {} queues",
                    threadsPerQueue, queues.size());
    }

    private void startTxConsumer(String queueName) {
        JMSContext context = connectionFactory.createContext(JMSContext.SESSION_TRANSACTED);
        Queue queue = context.createQueue(queueName);
        JMSConsumer consumer = context.createConsumer(queue);

        Thread t = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Message msg = consumer.receive(5000);
                    if (msg != null) {
                        try {
                            int deliveryCount = msg.getIntProperty("JMSXDeliveryCount");
                            if (msg instanceof TextMessage tm) {
                                logger.info("TX received message on {}: {} (deliveryCount={})",
                                        queueName, tm.getText(), deliveryCount);

                                // simulate processing OK
                                context.commit();

                            } else {
                                logger.warn("TX received non-text message on {} (deliveryCount={}): {}",
                                        queueName, deliveryCount, msg);
                                context.rollback(); // let broker retry
                            }
                        } catch (Exception e) {
                            logger.error("Error processing TX message on queue {}, rolling back", queueName, e);
                            try {
                                context.rollback(); // retry handled by broker
                            } catch (Exception rollbackEx) {
                                logger.error("Rollback failed for queue {}", queueName, rollbackEx);
                            }
                        }
                    }
                }
            } finally {
                try { consumer.close(); } catch (Exception ignored) {}
            }
        }, "TxConsumer-" + queueName);

        t.start();
        contexts.add(context);
        txThreads.add(t);
        logger.info("Started TX consumer thread for queue {}", queueName);
    }

    public void stop() {
        txThreads.forEach(Thread::interrupt);
        txThreads.clear();

        contexts.forEach(ctx -> {
            try { ctx.close(); } catch (Exception ignored) {}
        });
        contexts.clear();

        logger.info("ConsumerPool stopped all listeners");
    }
}
