package com.example.artemis.listener;

import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import jakarta.jms.Destination;

@Component
public class ArtemisListener {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisListener.class);
    private final JmsTemplate jmsTemplate;

    public ArtemisListener(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    // Receive Request and send Reply 
    @JmsListener(destination = "${app.queue.request}", containerFactory = "jmsListenerContainerFactory")
    public void receiveAndReply(TextMessage message) throws Exception {
        try {
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
        } catch (Exception e) {
            logger.error("Failed to process request message", e);
            throw e; 
        }
    }    
}
