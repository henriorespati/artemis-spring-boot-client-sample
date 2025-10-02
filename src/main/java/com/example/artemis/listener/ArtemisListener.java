package com.example.artemis.listener;

import jakarta.jms.TextMessage;

// import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jms.JmsException;
// import org.springframework.boot.CommandLineRunner;
// import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import jakarta.jms.Message;
// import jakarta.jms.Session;

@Component
public class ArtemisListener 
        implements CommandLineRunner
    {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisListener.class);

    private final JmsTemplate jmsTemplate;

    @Value("${app.queue.sync}")
    private String syncQueue;

    public ArtemisListener(@Qualifier("jmsTemplate") JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    // Synchronous consumption
    // Option 1: Use Spring JMS Listener
    // Session acknowledge mode must be set to "client" 
    // @JmsListener(destination = "${app.queue.sync}")
    // public void receiveSync(Message message, Session session) throws Exception {
    //     try {
    //         if (message instanceof TextMessage text) {
    //             logger.info("Received SYNC: {}", text.getText());

    //             // Acknowledge the message after processing
    //             try {
    //                 message.acknowledge();
    //                 logger.info("Message acknowledged");
    //             } catch (Exception e) {
    //                 logger.error("Failed to acknowledge message", e);
    //                 throw e;
    //             }
    //         }
    //     } catch (Exception e) {
    //         logger.error("Message processing failed", e);
    //         throw e; 
    //     }
    // }

    // Option 2: Use JMS Template
    public void receiveSync(String syncQueue) throws Exception {
        // LocalDateTime now = LocalDateTime.now();
        try {
            Message message = jmsTemplate.receive(syncQueue);
            if (message instanceof TextMessage textMessage) {
                logger.info("SYNC message received: {}", textMessage.getText());
            } 
        } catch (JmsException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error receiving SYNC message: {}", e.getMessage());
            throw e;
        }
        // LocalDateTime after = LocalDateTime.now();
        // logger.info("Time taken to wait for SYNC message: {} ms", java.time.Duration.between(now, after).toMillis());
    }

    // Enable continuous message pooling with timeout defined in spring.jms.template.receive-timeout
    @Override
    public void run(String... args) throws Exception {
        logger.info("Listening for SYNC messages on queue: {}", syncQueue);
        while (true) {
            receiveSync(syncQueue);
        }
    }
}
