package com.example.artemis.controller;

import com.example.artemis.service.ProducerService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/artemis/send")
public class ArtemisController {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisController.class);
    private final ProducerService producerService;

    @Value("${app.queue.async}")
    private String asyncQueueName;

    @Value("${app.queue.request}")
    private String requestQueueName;

    @Value("${app.queue.reply}")
    private String replyQueueName;

    @Value("${app.queue.sync}")
    private String syncQueueName;

    @Value("${app.queue.transaction}")
    private String transactionQueueName;

    public ArtemisController(ProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping("/sync")
    public ResponseEntity<String> sendSync(@RequestBody String message) {
        try {
            producerService.send(syncQueueName, message);
            return ResponseEntity.ok("SYNC message sent");
        } catch (Exception e) {
            logger.error("Failed to send sync message", e);
            return ResponseEntity.status(500).body("Error sending sync message");
        }
    }

    @PostMapping("/async")
    public ResponseEntity<String> sendAsync(@RequestBody String message) {
        try {
            producerService.sendAsync(asyncQueueName, message);
            return ResponseEntity.ok("ASYNC message sent successfully");
        } catch (Exception e) {
            logger.error("Failed to send async message", e);
            return ResponseEntity.status(500).body("Error sending async message: " + e.getMessage());
        }
    }

    @PostMapping("/request")
    public ResponseEntity<String> sendRequest(@RequestBody String message) {
        try {
            String reply = producerService.sendRequest(requestQueueName, replyQueueName, message);
            if (reply != null) {
                return ResponseEntity.ok("Received reply: " + reply);
            } else {
                return ResponseEntity.status(204).body("No reply received");
            }
        } catch (Exception e) {
            logger.error("Failed to send request message", e);
            return ResponseEntity.status(500).body("Error sending request message: " + e.getMessage());
        }
    }

    @PostMapping("/transaction")
    public ResponseEntity<String> sendTransaction(@RequestBody List<String> messages) {
        try {
            producerService.sendTransaction(transactionQueueName, messages);
            return ResponseEntity.ok("Transactional send committed");
        } catch (Exception e) {
            logger.error("Failed to send transactional messages", e);
            return ResponseEntity.status(500).body("Error sending transactional messages: " + e.getMessage());
        }
    }
}
