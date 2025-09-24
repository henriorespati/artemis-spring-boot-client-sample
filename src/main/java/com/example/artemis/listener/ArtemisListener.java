package com.example.artemis.listener;

import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
public class ArtemisListener {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisListener.class);

    // Asynchronous consumption 
    // jms listener session must be set to auto 
    @JmsListener(destination = "${app.queue.test}", containerFactory = "jmsListenerContainerFactory")
    public void receiveAsync(TextMessage message) throws Exception {
        try {
            logger.info("ASYNC message received: {}", message.getText()); 
        } catch (Exception e) {
            logger.error("Processing failed", e);
            throw e; 
        }        
    }
}
