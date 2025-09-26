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

    @Value("${app.queue.async}")
    private String asyncQueueName;

    public ArtemisController(ProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping("/send/async")
    public ResponseEntity<String> sendAsync(@RequestBody String message) {
        try {
            producerService.sendAsync(asyncQueueName, message);
            return ResponseEntity.ok("ASYNC message sent successfully");
        } catch (Exception e) {
            logger.error("Failed to send async message", e);
            return ResponseEntity.status(500).body("Error sending async message: " + e.getMessage());
        }
    }
}
