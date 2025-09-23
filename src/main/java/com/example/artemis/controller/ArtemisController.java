package com.example.artemis.controller;

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

    @Value("${app.queue.sync}")
    private String syncQueue;

    public ArtemisController(ProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping("/send/sync")
    public ResponseEntity<String> sendSync(@RequestBody String message) {
        try {
            producerService.send(syncQueue, message);
            return ResponseEntity.ok("SYNC message sent");
        } catch (Exception e) {
            logger.error("Failed to send sync message", e);
            return ResponseEntity.status(500).body("Error sending sync message");
        }
    }
}
