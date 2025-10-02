package com.example.artemis.controller;

import com.example.artemis.listener.ArtemisListener;
import com.example.artemis.service.ProducerService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/artemis")
public class ArtemisController {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisController.class);
    private final ProducerService producerService;
    private final ArtemisListener artemisListener;

    @Value("${app.queue.sync}")
    private String syncQueueName;

    public ArtemisController(ProducerService producerService, ArtemisListener artemisListener) {
        this.producerService = producerService;
        this.artemisListener = artemisListener;
    }

    @PostMapping("/send/sync")
    public ResponseEntity<String> sendSync(@RequestBody String message) {
        try {
            producerService.send(syncQueueName, message);
            return ResponseEntity.ok("SYNC message sent");
        } catch (Exception e) {
            logger.error("Failed to send sync message", e);
            return ResponseEntity.status(500).body("Error sending sync message");
        }
    }

    // Endpoint for sync consumption when using JMS template receive()
    @PostMapping("/receive/sync")
    public ResponseEntity<String> receiveSync() {
        try {
            artemisListener.receiveSync(syncQueueName);
            return ResponseEntity.ok("Sync receive successful");
        } catch (Exception e) {
            logger.error("Failed to receive sync message", e);
            return ResponseEntity.status(500).body("Error receiving sync message: " + e.getMessage());
        }
    }
}
