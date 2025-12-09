package com.example.artemis.listener;

import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;

import java.util.ArrayList;
import java.util.List;
// import java.util.Random;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ArtemisListener {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisListener.class);

    private final JmsTemplate jmsTemplate;

    // Accumulator for Spring JMS transactional batches
    private final Map<String, List<String>> batchAccumulator = new ConcurrentHashMap<>();

    @Value("${spring.jms.template.receive-timeout}")
    private int receiveTimeout;

    public ArtemisListener(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    // Core JMS Transactional consumer
    // Session transacted = true 
    // Triggered via REST endpoint "/artemis/receive/transaction"
    public void receiveTransaction(String transactionQueueName, String batchId) throws Exception {
        try {
            List<TextMessage> batch = new ArrayList<>();

            jmsTemplate.execute(session -> {
                Queue queue = session.createQueue(transactionQueueName);
                MessageConsumer consumer = session.createConsumer(queue);
                
                Message msg;
                while ((msg = consumer.receive(receiveTimeout)) != null) {
                    if (batchId.equals(msg.getStringProperty("JMSXGroupID"))) {
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

                // if(new Random().nextInt(5) == 0)
                //     throw new RuntimeException("Simulated failure and rollback");

                session.commit();
                logger.info("Transaction {} received and committed with {} messages", batchId, batch.size());
                
                return null;
            }, true); 
        } catch (Exception e) {
            logger.error("Transaction {} rolled back in Consumer", batchId);
            logger.debug(e.toString());
            throw e;
        }
    }

    // Spring JMS Transactional listener 
    // Session transacted = false
    @Transactional
    @JmsListener(destination = "${app.queue.transaction}")
    public void receiveBatch(Message message, Session session) throws Exception {
        try {            
            String batchId = message.getStringProperty("JMSXGroupID");
            int seq = message.getIntProperty("JMSXGroupSeq");        
            String body = ((TextMessage)message).getText();
            logger.info("Received for batch {}: seq={} body={}", batchId, seq, body);

            // Add message to accumulator
            batchAccumulator
                    .computeIfAbsent(batchId, k -> new ArrayList<>())
                    .add(body);

            // Not end of group → return, wait for next message
            if (seq != -1) {
                logger.info("Batch {} not complete yet, waiting for more messages...", batchId);
                return;
            }

            // When seq = -1 means end of batch
            List<String> fullBatch = batchAccumulator.remove(batchId);
            logger.info("Processing COMPLETE batch {} with {} messages", batchId, fullBatch.size());

            for (String msgBody : fullBatch) {
                logger.info("Batch {} item: {}", batchId, msgBody);
            }

            // Simulated failure
            // if (new Random().nextInt(5) == 0)
            //     throw new RuntimeException("Simulated failure and rollback");

            // NO manual commit here — Spring will commit automatically
            logger.info("Batch {} processed successfully", batchId);
        
        } catch (Exception e) {
            logger.error("Error processing message, rolling back transaction");
            logger.debug(e.toString());
            throw e;
        }
    }
}
