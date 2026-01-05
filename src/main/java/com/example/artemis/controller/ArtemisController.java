package com.example.artemis.controller;

import com.example.artemis.listener.ArtemisListener;
import com.example.artemis.service.ProducerService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/artemis")
public class ArtemisController {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisController.class);
    private final ProducerService producerService;
    private final ArtemisListener artemisListener;

    @Value("${app.queue.transaction}")
    private String transactionQueueName;

    @Value("${app.queue.spring-transaction}")
    private String springTransactionQueueName;

    public ArtemisController(ProducerService producerService, ArtemisListener artemisListener) {
        this.producerService = producerService;
        this.artemisListener = artemisListener;
    }

    @PostMapping("/send/transaction/spring")
    public ResponseEntity<String> sendSpringTransaction(@RequestBody List<String> messages) {
        try {
            producerService.sendSpringTransaction(springTransactionQueueName, messages);
            return ResponseEntity.ok("Transactional send committed");
        } catch (Exception e) {
            logger.error("Failed to send transactional messages");
            return ResponseEntity.status(500).body("Error sending transactional messages: " + e.getMessage());
        }
    }

    @PostMapping("/send/transaction/core")
    public ResponseEntity<String> sendCoreTransaction(@RequestBody List<String> messages) {
        try {
            producerService.sendCoreTransaction(transactionQueueName, messages);
            return ResponseEntity.ok("Transactional send committed");
        } catch (Exception e) {
            logger.error("Failed to send transactional messages");
            return ResponseEntity.status(500).body("Error sending transactional messages: " + e.getMessage());
        }
    }

    @PostMapping("/receive/transaction")
    public ResponseEntity<String> receiveTransaction(@RequestBody String batchId) {
        try {
            artemisListener.receiveTransaction(transactionQueueName, batchId);
            return ResponseEntity.ok("Transactional receive committed");
        } catch (Exception e) {
            logger.error("Failed to receive transactional messages");
            return ResponseEntity.status(500).body("Error receiving transactional messages: " + e.getMessage());
        }
    }
}
