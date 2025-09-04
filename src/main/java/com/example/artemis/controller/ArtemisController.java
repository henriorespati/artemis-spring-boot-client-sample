package com.example.artemis.controller;

import com.example.artemis.service.ArtemisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/artemis")
public class ArtemisController {

    private final ArtemisService service;

    public ArtemisController(ArtemisService service) {
        this.service = service;
    }

    @PostMapping("/send/sync/{queueName}")
    public ResponseEntity<String> sendSync(@PathVariable("queueName") String queueName,
                                           @RequestBody String message) {
        try {
            service.sendSync(queueName, message);
            return ResponseEntity.ok("Sync message sent");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error sending sync message: " + e.getMessage());
        }
    }

    @PostMapping("/send/async/{queueName}")
    public ResponseEntity<String> sendAsync(@PathVariable("queueName") String queueName,
                                            @RequestBody String message) {
        try {
            service.sendAsync(queueName, message);
            return ResponseEntity.ok("Async message sent");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error sending async message: " + e.getMessage());
        }
    }

    @PostMapping("/send/request/{queueName}")
    public ResponseEntity<String> sendRequest(@PathVariable("queueName") String queueName,
                                            @RequestBody String message) {
        try {
            service.sendRequest(queueName, message);
            return ResponseEntity.ok("Request message sent");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error sending request message: " + e.getMessage());
        }
    }
}
