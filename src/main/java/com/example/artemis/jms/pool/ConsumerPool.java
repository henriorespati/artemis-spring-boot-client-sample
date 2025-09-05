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
    private final List<Thread> replyThreads = new ArrayList<>();
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
                startReplier(queueName);                
            }
        }
        logger.info("ConsumerPool started {} threads per queue for {} queues",
                    threadsPerQueue, queues.size());
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

    public void stop() {
        replyThreads.forEach(Thread::interrupt);
        replyThreads.clear();

        contexts.forEach(ctx -> {
            try { ctx.close(); } catch (Exception ignored) {}
        });
        contexts.clear();

        logger.info("ConsumerPool stopped all listeners");
    }
}
