package com.example.artemis.jms.pool;

import com.example.artemis.config.ArtemisProperties;
import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConsumerPool {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerPool.class);

    private final ConnectionFactory cf;
    private final ArtemisProperties props;

    private final Map<String, List<Session>> sessionsPerQueue = new HashMap<>();
    private final Map<String, List<MessageConsumer>> consumersPerQueue = new HashMap<>();
    private final Map<String, List<Connection>> connectionsPerQueue = new HashMap<>();
    private ExecutorService executor;

    public ConsumerPool(ConnectionFactory cf, ArtemisProperties props) {
        this.cf = cf;
        this.props = props;
    }

    // Called by Spring initMethod
    public void start() {
        try {
            init();
            logger.info("ConsumerPool started on application startup");
        } catch (JMSException e) {
            logger.error("Failed to start ConsumerPool", e);
        }
    }

    // Called by Spring destroyMethod
    public void stop() {
        destroy();
        logger.info("ConsumerPool stopped on application shutdown");
    }

    public void init() throws JMSException {
        startListeners();
    }

    private void startListeners() throws JMSException {
        executor = Executors.newCachedThreadPool();
        int threads = props.getConsumerThreadsPerQueue();

        for (String queueName : props.getQueues()) {
            List<Session> sessions = new ArrayList<>();
            List<MessageConsumer> consumers = new ArrayList<>();
            List<Connection> connections = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                Connection connection = cf.createConnection(props.getUser(), props.getPassword());
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                MessageConsumer consumer = session.createConsumer(session.createQueue(queueName));
                if (consumer == null) {
                    throw new JMSException("Failed to create consumer for queue: " + queueName);
                } else {
                    logger.info("Created consumer for queue: {}", queueName);
                }

                consumer.setMessageListener(msg -> {
                    try {
                        if (msg instanceof TextMessage) {
                            String body = ((TextMessage) msg).getText();
                            logger.info("Received JMS message on queue {}: {}", queueName, body);
                        } else {
                            logger.warn("Received non-text JMS message on queue {}: {}", queueName, msg);
                        }
                    } catch (JMSException e) {
                        logger.error("Error processing JMS message on queue {}", queueName, e);
                    }
                });

                connection.start();

                sessions.add(session);
                consumers.add(consumer);
                connections.add(connection);
            }

            sessionsPerQueue.put(queueName, sessions);
            consumersPerQueue.put(queueName, consumers);
            connectionsPerQueue.put(queueName, connections);
        }

        logger.info("ConsumerPool started listeners for {} queues", props.getQueues().size());
    }

    private void stopListeners() {
        consumersPerQueue.values().forEach(list -> list.forEach(c -> {
            try { c.close(); } catch (Exception ignored) {}
        }));
        consumersPerQueue.clear();

        sessionsPerQueue.values().forEach(list -> list.forEach(s -> {
            try { s.close(); } catch (Exception ignored) {}
        }));
        sessionsPerQueue.clear();

        connectionsPerQueue.values().forEach(list -> list.forEach(c -> {
            try { c.close(); } catch (Exception ignored) {}
        }));
        connectionsPerQueue.clear();

        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }

        logger.info("All listeners stopped");
    }

    public void destroy() {
        stopListeners();
    }
}
