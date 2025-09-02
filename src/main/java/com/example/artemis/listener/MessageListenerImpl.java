package com.example.artemis.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.*;

public class MessageListenerImpl implements MessageListener {

    private static final Logger logger = LoggerFactory.getLogger(MessageListenerImpl.class);

    @Override
    public void onMessage(Message message) {
        try {
            if (message instanceof TextMessage) {
                String body = ((TextMessage) message).getText();
                logger.info("Received JMS message: {}", body);

                // 🔧 Business logic goes here
                // process(body);

            } else {
                logger.warn("Received non-text JMS message: {}", message);
            }
        } catch (Exception e) {
            logger.error("Error processing JMS message", e);
            // In AUTO_ACKNOWLEDGE mode, message is already acked unless you throw RuntimeException.
            // If you want retries, use CLIENT_ACKNOWLEDGE or transacted sessions.
        }
    }
}
