package com.example.artemis.listener;

import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import jakarta.jms.Destination;

@Component
public class ArtemisListener {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisListener.class);
    private final JmsTemplate jmsTemplate;

    public ArtemisListener(@Qualifier("jmsTemplate") JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
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
    @JmsListener(destination = "${app.queue.sync}", containerFactory = "jmsListenerContainerFactory")
    public void receiveSync(TextMessage message) throws Exception {
        logger.info("SYNC message received: {}", message.getText());
    }
    
}
