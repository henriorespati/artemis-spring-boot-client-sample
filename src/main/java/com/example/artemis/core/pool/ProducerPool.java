package com.example.artemis.core.pool;

import com.example.artemis.config.ArtemisProperties;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Semaphore;


public class ProducerPool {

    private static final Logger logger = LoggerFactory.getLogger(ProducerPool.class);

    private final ClientSessionFactory sessionFactory;
    private final ArtemisProperties props;

    private final Deque<ProducerHolder> pool = new ArrayDeque<>();
    private final Semaphore semaphore;

    public ProducerPool(ClientSessionFactory sessionFactory, ArtemisProperties props) {
        this.sessionFactory = sessionFactory;
        this.props = props;
        this.semaphore = new Semaphore(props.getProducerPoolSize());
    }

    @PostConstruct
    public void init() throws ActiveMQException {
        for (int i = 0; i < props.getProducerPoolSize(); i++) {
            logger.info("Initializing ProducerHolder {}", i);
            logger.debug("Connected via connector: {}", sessionFactory.getConnectorConfiguration().getCombinedParams());
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
            ProducerHolder holder = new ProducerHolder(session, null);
            pool.push(holder);
        }
        logger.info("Initialized ProducerPool with size {}", props.getProducerPoolSize());
    }

    public ProducerHolder acquire(String queueName) throws InterruptedException, ActiveMQException {
        semaphore.acquire();
        synchronized (pool) {
            ProducerHolder holder = pool.poll();
            if (holder == null) {
                // Create on-demand if none in pool
                logger.info("No available ProducerHolder, creating new one for queue {}", queueName);
                logger.debug("Connected via connector: {}", sessionFactory.getConnectorConfiguration().getCombinedParams());
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
                ClientProducer producer = session.createProducer(queueName);
                holder = new ProducerHolder(session, producer);
                logger.info("Created on-demand ProducerHolder for queue {}", queueName);
                return holder;
            }
            if (holder.getProducer() == null) {
                // create producer if missing (only on initial pool fill)
                ClientProducer producer = holder.getSession().createProducer(queueName);
                return new ProducerHolder(holder.getSession(), producer);
            }
            return holder;
        }
    }

    public void release(ProducerHolder holder) {
        synchronized (pool) {
            pool.push(holder);
        }
        semaphore.release();
    }

    @PreDestroy
    public void close() {
        synchronized (pool) {
            for (ProducerHolder holder : pool) {
                try {
                    holder.close();
                } catch (Exception e) {
                    logger.warn("Error closing producer holder", e);
                }
            }
            pool.clear();
        }
        logger.info("ProducerPool closed");
    }

    // Sync send
    public void sendSync(String queueName, String message) throws Exception {
        ProducerHolder holder = acquire(queueName);
        try {
            ClientProducer producer = holder.getProducer();
            ClientSession session = holder.getSession();
            ClientMessage clientMessage = session.createMessage(true);
            clientMessage.getBodyBuffer().writeString(message);
            clientMessage.putStringProperty("JMS_QUEUE", queueName);
            producer.send(clientMessage); 
        } finally {
            release(holder);
        }
    }

    // Async send with SendAcknowledgementHandler
    public void sendAsync(String queueName, String message, SendAcknowledgementHandler handler) throws Exception {
        ProducerHolder holder = acquire(queueName);
        try {
            ClientProducer producer = holder.getProducer();
            ClientSession session = holder.getSession();
            ClientMessage clientMessage = session.createMessage(true);
            clientMessage.getBodyBuffer().writeString(message);
            clientMessage.putStringProperty("JMS_QUEUE", queueName);
            producer.send(clientMessage, handler);
        } finally {
            release(holder);
        }
    }
}
