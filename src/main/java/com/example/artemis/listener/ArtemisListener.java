package com.example.artemis.listener;

import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class ArtemisListener {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisListener.class);

    private final JmsTemplate jmsTemplate;

    @Value("${spring.jms.template.receive-timeout}")
    private int receiveTimeout;

    public ArtemisListener(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    // Transactional consumption 
    // Session transacted = true 
    // Triggered via REST endpoint 
    public void receiveTransaction(String transactionQueueName, String batchId) throws Exception {
        try {
            jmsTemplate.execute(session -> {
                Queue queue = session.createQueue(transactionQueueName);
                MessageConsumer consumer = session.createConsumer(queue);

                List<TextMessage> batch = new ArrayList<>();
                Message msg;
                while ((msg = consumer.receive(receiveTimeout)) != null) {
                    if (batchId.equals(msg.getStringProperty("batchId"))) {
                        batch.add((TextMessage) msg);
                    }
                }

                if (!batch.isEmpty()) {
                    logger.info("Processing batchId={} with {} messages", batchId, batch.size());
                    for (TextMessage m : batch) {
                        logger.info("Message: {}", m.getText());
                    }
                } else {
                    logger.warn("No messages found for batchId={}", batchId);
                }

                session.commit(); 
                logger.info("Transaction {} received and committed with {} messages", batchId, batch.size());
                return null;
            }, true);
        } catch (Exception e) {
            logger.error("Transaction rolled back", e);
            throw e; 
        }
    }
}
