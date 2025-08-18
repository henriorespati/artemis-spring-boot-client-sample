package com.example.artemis.core.pool;

import com.example.artemis.config.ArtemisProperties;
import com.example.artemis.listener.MessageListener;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class ConsumerPool {

    private static final Logger logger = LoggerFactory.getLogger(ConsumerPool.class);

    private final ClientSessionFactory sessionFactory;
    private final ArtemisProperties props;
    private final Map<String, List<ClientSession>> sessionsPerQueue = new HashMap<>();
    private final Map<String, List<ClientConsumer>> consumersPerQueue = new HashMap<>();
    private final Map<String, List<Thread>> listenerThreads = new HashMap<>();
    private ExecutorService executor;

    public ConsumerPool(ClientSessionFactory sessionFactory, ArtemisProperties props) {
        this.sessionFactory = sessionFactory;
        this.props = props;
    }

    @PostConstruct
    public void init() throws Exception {
        // Only start listeners if sessionFactory is not null
        if (sessionFactory == null) {
            throw new IllegalStateException("SessionFactory not initialized");
        }
        startListeners();
    }

    public void startListeners() throws Exception {
        // stopListeners();

        int threads = props.getConsumerThreadsPerQueue();
        executor = Executors.newCachedThreadPool();

        logger.info("Starting listener with {} threads", threads);

        for (String queue : props.getQueues()) {
            List<ClientSession> sessions = new ArrayList<>();
            List<ClientConsumer> consumers = new ArrayList<>();
            List<Thread> threadsList = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                logger.info("Starting listener thread {} for queue {}", i, queue);
                ClientSession session = sessionFactory.createSession(
                        props.getUser(),
                        props.getPassword(),
                        true, 
                        true, 
                        true, 
                        true, 
                        1
                );
                session.start();

                if (!session.queueQuery(SimpleString.toSimpleString(queue)).isExists()) {
                    session.createQueue(new QueueConfiguration(queue));
                } else {
                    logger.warn("Queue {} already exists, skipping creation", queue);
                }

                ClientConsumer consumer = session.createConsumer(queue);
                MessageListener listener = new MessageListener(session, consumer);
                Thread t = new Thread(listener, "ArtemisListener-" + queue + "-" + i);
                t.start();

                sessions.add(session);
                consumers.add(consumer);
                threadsList.add(t);

                logger.info("Started listener thread {} for queue {}", i, queue);
            }
            sessionsPerQueue.put(queue, sessions);
            consumersPerQueue.put(queue, consumers);
            listenerThreads.put(queue, threadsList);
        }
    }

    public void stopListeners() {
        listenerThreads.values().forEach(threads -> threads.forEach(Thread::interrupt));
        listenerThreads.clear();

        consumersPerQueue.values().forEach(consumers -> consumers.forEach(c -> {
            try { c.close(); } catch (Exception ignored) {}
        }));
        consumersPerQueue.clear();

        sessionsPerQueue.values().forEach(sessions -> sessions.forEach(s -> {
            try { s.close(); } catch (Exception ignored) {}
        }));
        sessionsPerQueue.clear();

        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }

        logger.info("All listeners stopped");
    }

    @PreDestroy
    public void destroy() {
        stopListeners();
    }
}
