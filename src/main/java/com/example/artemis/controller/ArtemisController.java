package com.example.artemis.controller;

import com.example.artemis.service.ProducerService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/artemis")
public class ArtemisController {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisController.class);
    private final ProducerService producerService;

    @Value("${app.queue.transaction}")
    private String transactionQueueName;

    public ArtemisController(ProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping("/send/transaction")
    public String sendTransaction(@RequestBody List<String> messages) {
        producerService.sendTransaction(transactionQueueName, messages);
        return "Transactional send committed";
    }
}
