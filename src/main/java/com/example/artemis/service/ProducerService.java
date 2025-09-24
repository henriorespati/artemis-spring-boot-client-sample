package com.example.artemis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProducerService {

    private static final Logger logger = LoggerFactory.getLogger(ProducerService.class);

    @Value("${spring.jms.template.receive-timeout}")
    private int receiveTimeout;

    private final JmsTemplate jmsTemplate;

    public ProducerService(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
    }

    // Transactional send 
    // Session transacted must be set to true in the JmsTemplate
    public void sendTransaction(String queueName, List<String> messages) {
        jmsTemplate.execute(session -> {
            var producer = session.createProducer(session.createQueue(queueName));
            for (String msg : messages) {
                producer.send(session.createTextMessage(msg));
                logger.info("Transactional message sent: {}", msg);
            }
            // Commit the transaction
            session.commit();
            logger.info("Transaction committed with {} messages", messages.size());
            return null;
        }, true); // sessionTransacted=true
    }

}
