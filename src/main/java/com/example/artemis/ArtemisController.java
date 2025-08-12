package com.example.artemis;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/artemis")
public class ArtemisController {

    private final ArtemisCoreClientPool artemisCoreClientPool;

    public ArtemisController(ArtemisCoreClientPool artemisCoreClientPool) {
        this.artemisCoreClientPool = artemisCoreClientPool;
    }

    @PostMapping("/send")
    public CompletableFuture<ResponseEntity<String>> sendMessage(
            @RequestParam String queue,
            @RequestParam String msg) {

        try {
            artemisCoreClientPool.send(queue, msg);
            return CompletableFuture.completedFuture(
                    ResponseEntity.ok("Sync message sent to " + queue));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(
                    ResponseEntity.status(500).body("Failed to send sync message: " + e.getMessage()));
        }        
    }

    @Async
    @PostMapping("/sendAsync")
    public CompletableFuture<ResponseEntity<String>> sendMessageAsyncOnly(
            @RequestParam String queue,
            @RequestParam String msg) {

        return artemisCoreClientPool.sendAsync(queue, msg)
                .thenApply(v -> ResponseEntity.ok("Async message sent to " + queue))
                .exceptionally(ex -> ResponseEntity.status(500)
                        .body("Failed to send async message: " + ex.getCause().getMessage()));
    }

    @GetMapping("/receive")
    public ResponseEntity<String> receiveMessage(
            @RequestParam String queue) {

        try {
            ClientMessage msg = artemisCoreClientPool.receive(queue);
            if (msg == null) {
                return ResponseEntity.noContent().build();
            }
            String body = msg.getBodyBuffer().readString();
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to receive message: " + e.getMessage());
        }
    }

    @Async
    @GetMapping("/receiveAsync")
    public CompletableFuture<ResponseEntity<String>> receiveMessageAsync(
            @RequestParam String queue) {

        return artemisCoreClientPool.receiveAsync(queue)
                .<ResponseEntity<String>>thenApply(msg -> {
                    if (msg == null) {
                        return ResponseEntity.noContent().build();
                    }
                    try {
                        String body = msg.getBodyBuffer().readString();
                        return ResponseEntity.ok(body);
                    } catch (Exception e) {
                        return ResponseEntity.status(500).body("Error reading message: " + e.getMessage());
                    }
                })
                .exceptionally(ex -> ResponseEntity.status(500)
                        .body("Failed to receive async message: " + ex.getCause().getMessage()));
    }
}
