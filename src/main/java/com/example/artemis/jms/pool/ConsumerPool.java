package com.example.artemis.jms.pool;

import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;

public class ConsumerPool {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerPool.class);

    public enum Mode { SYNC, ASYNC, REPLY, TX }

    private final ConnectionFactory connectionFactory;
    private final Mode mode;
    private final List<JMSContext> contexts = new ArrayList<>();
    private final List<Thread> syncThreads = new ArrayList<>();
    private final List<Thread> replyThreads = new ArrayList<>();
    private final List<Thread> txThreads = new ArrayList<>();
    private final List<String> queues;

    private final int threadsPerQueue;

    public ConsumerPool(ConnectionFactory connectionFactory, List<String> queues,
                        int threadsPerQueue, Mode mode) {
        this.connectionFactory = connectionFactory;
        this.queues = queues;
        this.threadsPerQueue = threadsPerQueue;
        this.mode = mode;
    }

    public void start() {
        for (String queueName : queues) {
            for (int i = 0; i < threadsPerQueue; i++) {
                if (mode == Mode.ASYNC) {
                    startAsyncConsumer(queueName);
                } else if (mode == Mode.SYNC) {
                    startSyncConsumer(queueName);
                } else if (mode == Mode.REPLY) {
                    startReplier(queueName);
                } else if (mode == Mode.TX) {
                    startTxConsumer(queueName);
                }
            }
        }
        logger.info("ConsumerPool started {} threads per queue for {} queues",
                    threadsPerQueue, queues.size());
    }

    private void startAsyncConsumer(String queueName) {
        JMSContext context = connectionFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE);
        Queue queue = context.createQueue(queueName);
        JMSConsumer consumer = context.createConsumer(queue);

        consumer.setMessageListener(msg -> {
            try {
                if (msg instanceof TextMessage tm) {
                    logger.info("ASYNC received message on {}: {}", queueName, tm.getText());
                } else {
                    logger.warn("ASYNC received non-text message on {}: {}", queueName, msg);
                }

                msg.acknowledge();
                logger.debug("Acknowledged ASYNC message on {}", queueName);
            } catch (JMSException e) {
                logger.error("Error processing ASYNC message on queue {}", queueName, e);
            }
        });

        contexts.add(context);
        logger.info("Started ASYNC consumer for queue {}", queueName);
    }

    private void startSyncConsumer(String queueName) {
        JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
        Queue queue = context.createQueue(queueName);
        JMSConsumer consumer = context.createConsumer(queue);

        Thread t = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    long start = System.nanoTime(); 
                    Message msg = consumer.receive(5000); 
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    logger.debug("SYNC receive on {} took {} ms", queueName, elapsedMs);
                    if (msg != null) {
                        try {
                            if (msg instanceof TextMessage tm) {
                                logger.info("SYNC received message on {}: {}", queueName, tm.getText());
                            } else {
                                logger.warn("SYNC received non-text message on {}: {}", queueName, msg);
                            }
                        } catch (JMSException e) {
                            logger.error("Error processing SYNC message on queue {}", queueName, e);
                        } 
                    }
                }
            } finally {
                try { consumer.close(); } catch (Exception ignored) {}
            }
        }, "SyncConsumer-" + queueName);

        t.start();
        contexts.add(context);
        txThreads.add(t);
        logger.info("Started SYNC consumer thread for queue {}", queueName);
    }

    private void startReplier(String queueName) {
        JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
        Queue queue = context.createQueue(queueName);
        JMSConsumer consumer = context.createConsumer(queue);

        Thread t = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    Message request = consumer.receive(5000);
                    if (request != null) {
                        try {
                            processRequest(context, queueName, request);                            
                        } catch (JMSRuntimeException e) {
                            logger.error("Error in replier loop for {}", queueName, e);
                        }
                    }
                }
            } finally {
                try { consumer.close(); } catch (Exception ignored) {}
            }
        }, "Replier-" + queueName);

        t.start();
        contexts.add(context);
        replyThreads.add(t);
        logger.info("Started replier thread for queue {}", queueName);
    }

    private void processRequest(JMSContext context, String queueName, Message request) {
        try {
            String text = (request instanceof TextMessage tm) ? tm.getText() : request.toString();
            String correlationId = request.getJMSCorrelationID();
            Destination replyTo = request.getJMSReplyTo();

            logger.info("Received request on {}: {} (correlationId={})", queueName, text, correlationId);

            if (replyTo != null) {
                TextMessage reply = context.createTextMessage("Reply to: " + text);
                reply.setJMSCorrelationID(correlationId);

                logger.debug("Sending reply for correlationId={} back to {}", correlationId, replyTo);
                context.createProducer().send(replyTo, reply);
            } else {
                logger.warn("No JMSReplyTo set on message, cannot reply (correlationId={})", correlationId);
            }
        } catch (JMSException e) {
            logger.error("Failed to process request on {}", queueName, e);
        }
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
        syncThreads.add(t);
        logger.info("Started TX consumer thread for queue {}", queueName);
    }

    public void stop() {
        syncThreads.forEach(Thread::interrupt);
        syncThreads.clear();

        replyThreads.forEach(Thread::interrupt);
        replyThreads.clear();

        txThreads.forEach(Thread::interrupt);
        txThreads.clear();

        contexts.forEach(ctx -> {
            try { ctx.close(); } catch (Exception ignored) {}
        });
        contexts.clear();

        logger.info("ConsumerPool stopped all listeners");
    }
}
