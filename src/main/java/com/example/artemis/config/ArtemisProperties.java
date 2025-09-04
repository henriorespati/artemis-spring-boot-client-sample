package com.example.artemis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app.artemis")
public class ArtemisProperties {

    private List<String> queues;
    private int confirmationWindowSize = -1; // default: disabled
    private Consumer consumer = new Consumer();

    public List<String> getQueues() { return queues; }
    public void setQueues(List<String> queues) { this.queues = queues; }

    public int getConfirmationWindowSize() { return confirmationWindowSize; }
    public void setConfirmationWindowSize(int confirmationWindowSize) { this.confirmationWindowSize = confirmationWindowSize; }

    public Consumer getConsumer() { return consumer; }
    public void setConsumer(Consumer consumer) { this.consumer = consumer; }

    public static class Consumer {
        private String mode = "SYNC";
        private int threadsPerQueue = 1;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }

        public int getThreadsPerQueue() { return threadsPerQueue; }
        public void setThreadsPerQueue(int threadsPerQueue) { this.threadsPerQueue = threadsPerQueue; }
    }
}
