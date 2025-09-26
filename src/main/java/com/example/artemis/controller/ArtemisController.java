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

    @Value("${app.queue.request}")
    private String requestQueueName;

    @Value("${app.queue.reply}")
    private String replyQueueName;

    public ArtemisController(ProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping("/send/request")
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
}
