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
    // Session acknowledge mode must be set to "client" 
    @JmsListener(destination = "${app.queue.sync}", containerFactory = "jmsListenerContainerFactory")
    public void receiveSync(Message message, Session session) throws Exception {
        try {
            if (message instanceof TextMessage text) {
                logger.info("Received SYNC: {}", text.getText());            
                message.acknowledge();
                logger.info("Message acknowledged");
            }
        } catch (Exception e) {
            logger.error("Processing failed, message is NOT acknowledged", e);
            throw e; 
        }
    }
}
