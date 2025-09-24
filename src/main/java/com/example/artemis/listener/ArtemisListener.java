package com.example.artemis.listener;

import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ArtemisListener {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisListener.class);

    // Transactional consumption 
    // Session transacted must be set to true in the listener container factory
    // Use JMS transaction manager
    @Transactional
    @JmsListener(destination = "${app.queue.transaction}")
    public void receiveTransaction(TextMessage message) throws Exception {
        try {
            String text = message.getText();
            logger.info("Received transactional message: {}", text);
        } catch (Exception e) {
            logger.error("Transaction rolled back for message: {}", message.getText(), e);
            throw e; // ensures Spring rolls back the session
        }
    } 
}
