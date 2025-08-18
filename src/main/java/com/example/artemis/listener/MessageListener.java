package com.example.artemis.listener;

import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageListener implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(MessageListener.class);

    private final ClientSession session;
    private final ClientConsumer consumer;

    public MessageListener(ClientSession session, ClientConsumer consumer) {
        this.session = session;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                ClientMessage msg = consumer.receive(5000); // wait up to 5 sec
                if (msg != null) {
                    try {
                        String body = msg.getBodyBuffer().readString();
                        logger.debug("Received message: {} from consumer {}", body, consumer.getConsumerContext());
                        // Process message here
                        msg.acknowledge();
                    } catch (Exception e) {
                        logger.error("Error processing message", e);
                        // optionally msg.reject() or rollback session if using transactions
                    }
                }
            }
        } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()) {
                logger.error("Listener thread error", e);
            }
        } finally {
            try {
                consumer.close();
                session.close();
            } catch (Exception e) {
                logger.warn("Error closing consumer/session", e);
            }
            logger.info("Listener thread exiting");
        }
    }
}
