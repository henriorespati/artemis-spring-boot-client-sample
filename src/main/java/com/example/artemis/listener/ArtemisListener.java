package com.example.artemis.listener;

import jakarta.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import jakarta.jms.Message;
import jakarta.jms.Session;

@Component
public class ArtemisListener {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisListener.class);

    // Synchronous consumption 
    // Session ack mode = CLIENT_ACKNOWLEDGE 
    @JmsListener(destination = "${app.queue.test}", containerFactory = "syncJmsListenerContainerFactory")
    public void receiveSync(Message message, Session session) throws Exception {
        try {
            if (message instanceof TextMessage text) {
                logger.info("Received SYNC: {}", text.getText());

                // Acknowledge the message after processing
                try {
                    message.acknowledge();
                    logger.info("Message acknowledged");
                } catch (Exception e) {
                    logger.error("Failed to acknowledge message", e);
                    throw e;
                }
            }
        } catch (Exception e) {
            logger.error("Message processing failed", e);
            throw e; 
        }
    }

}
