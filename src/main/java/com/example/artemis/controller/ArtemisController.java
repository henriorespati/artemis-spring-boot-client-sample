package com.example.artemis.controller;

import com.example.artemis.service.ProducerService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/artemis")
public class ArtemisController {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisController.class);
    private final ProducerService producerService;

    @Value("${app.queue.async}")
    private String asyncQueue;

    public ArtemisController(ProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping("/send/async")
    public String sendAsync(@RequestBody String message) {
        producerService.sendAsync(asyncQueue, message);
        return "ASYNC message sent successfully";
    }
}
