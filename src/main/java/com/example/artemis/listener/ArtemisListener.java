package com.example.artemis.listener;

import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.IllegalStateRuntimeException;

import java.util.ArrayList;
import java.util.List;

@Component
public class ArtemisListener {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisListener.class);

    private final ConnectionFactory connectionFactory;
    private final JmsTemplate jmsTemplate;
    private final List<JMSContext> contexts = new ArrayList<>();
    private final List<Thread> syncThreads = new ArrayList<>();
    private final int timeoutMs = 5000;

    public ArtemisListener(ConnectionFactory connectionFactory, @Qualifier("jmsTemplate") JmsTemplate jmsTemplate,
            @Value("${app.queue.sync}") String queueName) {
        this.connectionFactory = connectionFactory;
        this.jmsTemplate = jmsTemplate;
        startSyncConsumer(queueName);
    }

    /** Asynchronous consumption */
    @JmsListener(destination = "${app.queue.async}", containerFactory = "jmsListenerContainerFactory")
    public void receiveAsync(TextMessage message) throws Exception {
        logger.info("ASYNC message received: {}", message.getText());
    }

    /** Transactional consumption */
    @JmsListener(destination = "${app.queue.transaction}", containerFactory = "transactedJmsListenerContainerFactory")
    public void receiveTransaction(TextMessage message) throws Exception {
        try {
            String text = message.getText();
            logger.info("Received transactional message: {}", text);
        } catch (Exception e) {
            logger.error("Transaction rolled back for message: {}", message.getText(), e);
            throw e; // ensures Spring rolls back the session
        }
    }

    /** Request-Reply consumption */
    @JmsListener(destination = "${app.queue.request}", containerFactory = "jmsListenerContainerFactory")
    public void receiveAndReply(TextMessage message) throws Exception {
        String text = message.getText();
        logger.info("Received request: {}", text);

        Destination replyDest = message.getJMSReplyTo();
        if (replyDest != null) {
            String replyText = "Reply to: " + text;
            jmsTemplate.send(replyDest, session -> session.createTextMessage(replyText));
            logger.info("Sent reply: {} to queue {}", replyText, replyDest);
        } else {
            logger.warn("No JMSReplyTo set, cannot send reply for message: {}", text);
        }
    }

    /** Synchronous consumption */
    private void startSyncConsumer(String queueName) {
        JMSContext context = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
        Queue queue = context.createQueue(queueName);
        JMSConsumer consumer = context.createConsumer(queue);

        Thread t = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    var msg = consumer.receive(timeoutMs);

                    if (msg != null) {
                        if (msg instanceof TextMessage tm) {
                            logger.info("SYNC message received: {}", tm.getText());
                        } else {
                            logger.warn("SYNC received non-text message: {}", msg);
                        }
                    }
                }
            } catch (IllegalStateRuntimeException e) {
                logger.info("Consumer on {} closed, exiting loop", queueName);
            } catch (Exception e) {
                logger.error("Error in sync consumer", e);
            } finally {
                try { consumer.close(); } catch (Exception ignored) {}
                try { context.close(); } catch (Exception ignored) {}
                stop();
            }
        }, "SyncConsumer-" + queueName);

        t.start();
        contexts.add(context);
        syncThreads.add(t);
        logger.info("Started SYNC consumer thread for queue {}", queueName);
    }

    /** Stop all sync consumers */
    public void stop() {
        syncThreads.forEach(Thread::interrupt);
        syncThreads.clear();
        contexts.forEach(ctx -> {
            try {
                ctx.close();
            } catch (Exception ignored) {
            }
        });
        contexts.clear();
        logger.info("Stopped all consumers");
    }
}
