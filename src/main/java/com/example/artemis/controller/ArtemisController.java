package com.example.artemis.controller;

import com.example.artemis.service.ProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/artemis")
public class ArtemisController {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisController.class);
    private final ProducerService producerService;

    @Value("${app.queue.async}")
    private String asyncQueueName;

    @Value("${app.queue.request}")
    private String requestQueueName;

    @Value("${app.queue.sync}")
    private String syncQueueName;

    @Value("${app.queue.transaction}")
    private String transactionQueueName;

    public ArtemisController(ProducerService producerService) {
        this.producerService = producerService;
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

    @PostMapping("/send/async")
    public CompletableFuture<ResponseEntity<String>> sendAsync(@RequestBody String message) {
        return producerService.sendAsync(asyncQueueName, message)
                .thenApply(v -> ResponseEntity.ok("ASYNC message sent successfully"))
                .exceptionally(ex -> {
                    logger.error("Async send failed", ex);
                    return ResponseEntity.status(500).body("Async send failed");
                });
    }

    @PostMapping("/send/request")
    public String sendRequest(@RequestBody String message) {
        String reply = producerService.sendRequest(requestQueueName, message);
        return reply != null ? "Received reply: " + reply : "No reply";
    }

    @PostMapping("/send/transaction")
    public String sendTransaction(@RequestBody List<String> messages) {
        producerService.sendTransaction(transactionQueueName, messages);
        return "Transactional send committed";
    }
}
