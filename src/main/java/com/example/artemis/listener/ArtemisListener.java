package com.example.artemis.listener;

import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import jakarta.jms.Destination;
import jakarta.jms.Message;
import jakarta.jms.Session;

@Component
public class ArtemisListener {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisListener.class);
    private final JmsTemplate jmsTemplate;

    public ArtemisListener(@Qualifier("defaultJmsTemplate") JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    private String getAckModeName(int mode) {
        switch(mode) {
            case Session.AUTO_ACKNOWLEDGE: return "AUTO_ACKNOWLEDGE";
            case Session.CLIENT_ACKNOWLEDGE: return "CLIENT_ACKNOWLEDGE";
            case Session.DUPS_OK_ACKNOWLEDGE: return "DUPS_OK_ACKNOWLEDGE";
            case Session.SESSION_TRANSACTED: return "SESSION_TRANSACTED";
            default: return "UNKNOWN";
        }
    }

    /** Scenario 1: Synchronous consumption */
    // Session ack mode = CLIENT_ACKNOWLEDGE 
    @JmsListener(destination = "${app.queue.sync}", containerFactory = "syncJmsListenerContainerFactory")
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

    /** Scenario 2: Transactional consumption */
    // Session transacted = true 
    @JmsListener(destination = "${app.queue.transaction}", containerFactory = "txJmsListenerContainerFactory")
    public void receiveTransaction(TextMessage message) throws Exception {
        try {
            String text = message.getText();
            logger.info("Received transactional message: {}", text);
        } catch (Exception e) {
            logger.error("Transaction rolled back for message: {}", message.getText(), e);
            throw e; // ensures Spring rolls back the session
        }
    }

    /** Scenario 3: Asynchronous consumption */
    // Session ack mode = AUTO_ACKNOWLEDGE 
    @JmsListener(destination = "${app.queue.async}", containerFactory = "defaultJmsListenerContainerFactory")
    public void receiveAsync(TextMessage message) throws Exception {
        try {
            logger.info("ASYNC message received: {}", message.getText()); 
        } catch (Exception e) {
            logger.error("Processing failed", e);
            throw e; 
        }        
    }

    /** Scenario 4: Request-Reply consumption */
    @JmsListener(destination = "${app.queue.request}", containerFactory = "defaultJmsListenerContainerFactory")
    public void receiveAndReply(TextMessage message) throws Exception {
        try {
            String text = message.getText();
            logger.info("Received request: {}", text);

            Destination replyDest = message.getJMSReplyTo();
            if (replyDest != null) {
                String replyText = "Reply to: " + text;
                jmsTemplate.send(replyDest, session -> {
                    TextMessage replyMessage = session.createTextMessage(replyText);
                    replyMessage.setJMSCorrelationID(message.getJMSCorrelationID());
                    return replyMessage;
                });
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
