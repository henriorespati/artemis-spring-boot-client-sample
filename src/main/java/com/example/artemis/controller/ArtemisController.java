package com.example.artemis.controller;

import com.example.artemis.service.ArtemisService;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/artemis")
public class ArtemisController {

    private final ArtemisService service;

    public ArtemisController(ArtemisService service) {
        this.service = service;
    }

    @PostMapping("/send/tx/{queueName}")
    public ResponseEntity<String> sentTx(@PathVariable("queueName") String queueName,
                                            @RequestBody String message) {
        try {
            service.sendTx(queueName, message);
            return ResponseEntity.ok("Transactional message sent");
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("Error sending transactional message: " + e.getMessage());
        }
    }

    @PostMapping("/send/tx/batch/{queueName}")
    public ResponseEntity<String> sendTxBatch(@PathVariable("queueName") String queueName,
                                            @RequestBody List<String> messages) {
        try {
            service.sendTxBatch(queueName, messages);
            return ResponseEntity.ok("Transactional batch sent: " + messages.size() + " messages");
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("Error sending transactional batch: " + e.getMessage());
        }
    }
    
}
