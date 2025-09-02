package com.example.artemis.jms.pool;

import com.example.artemis.config.ArtemisProperties;
import jakarta.jms.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;

public class ProducerPool {

    private static final Logger logger = LoggerFactory.getLogger(ProducerPool.class);

    private final ConnectionFactory cf;
    private final ArtemisProperties props;
    private final Deque<Session> pool = new ArrayDeque<>();
    private final Semaphore semaphore;

    public ProducerPool(ConnectionFactory cf, ArtemisProperties props) {
        this.cf = cf;
        this.props = props;
        this.semaphore = new Semaphore(props.getProducerPoolSize());
    }

    public void init() throws JMSException {
        for (int i = 0; i < props.getProducerPoolSize(); i++) {
            Connection conn = cf.createConnection(props.getUser(), props.getPassword());
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            conn.start();
            pool.push(session);
        }
        logger.info("Initialized ProducerPool with {} sessions", props.getProducerPoolSize());
    }

    private Session acquireSession() throws InterruptedException {
        semaphore.acquire();
        synchronized (pool) {
            return pool.poll();
        }
    }

    private void releaseSession(Session session) {
        synchronized (pool) {
            pool.push(session);
        }
        semaphore.release();
    }

    public void sendSync(String queueName, String message) throws Exception {
        Session session = acquireSession();
        try (MessageProducer producer = session.createProducer(session.createQueue(queueName))) {
            TextMessage msg = session.createTextMessage(message);
            producer.send(msg);
            logger.debug("Sent SYNC message to queue {}", queueName);
        } finally {
            releaseSession(session);
        }
    }

    public void sendAsync(String queueName, String message) throws Exception {
        Session session = acquireSession();
        try (MessageProducer producer = session.createProducer(session.createQueue(queueName))) {
            TextMessage msg = session.createTextMessage(message);
            producer.send(msg, new CompletionListener() {
                @Override
                public void onCompletion(Message message) {
                    logger.debug("Async send complete for queue {}", queueName);
                }

                @Override
                public void onException(Message message, Exception exception) {
                    logger.error("Async send failed for queue {}", queueName, exception);
                }
            });
        } finally {
            releaseSession(session);
        }
    }

    public void destroy() {
        synchronized (pool) {
            for (Session session : pool) {
                try {
                    session.close();
                } catch (JMSException e) {
                    logger.warn("Error closing session", e);
                }
            }
            pool.clear();
        }
        logger.info("ProducerPool closed");
    }
}
