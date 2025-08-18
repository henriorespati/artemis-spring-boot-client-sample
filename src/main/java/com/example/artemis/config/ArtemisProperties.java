package com.example.artemis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "artemis.core")
public class ArtemisProperties {
    private List<String> brokerUrls;
    private String user;
    private String password;

    private String trustStorePath;
    private String trustStorePassword;

    private int producerPoolSize = 5;
    private int consumerThreadsPerQueue = 3;
    private String consumerMode = "SYNC";
    private int retryInterval = 2000; 
    private double retryIntervalMultiplier = 1.5; 
    private int maxRetryInterval = 10000; 
    private int reconnectAttempts = 5; 
    private int confirmationWindowSize = 1024;

    private List<String> queues;

    // Getters and setters 
    public List<String> getBrokerUrls() { return brokerUrls; }
    public void setBrokerUrls(List<String> brokerUrls) { this.brokerUrls = brokerUrls; }
    public String getUser() { return user; }
    public void setUser(String user) { this.user = user; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getTrustStorePath() { return trustStorePath; }
    public void setTrustStorePath(String trustStorePath) { this.trustStorePath = trustStorePath; }
    public String getTrustStorePassword() { return trustStorePassword; }
    public void setTrustStorePassword(String trustStorePassword) { this.trustStorePassword = trustStorePassword; }
    public int getProducerPoolSize() { return producerPoolSize; }
    public void setProducerPoolSize(int producerPoolSize) { this.producerPoolSize = producerPoolSize; }
    public int getConsumerThreadsPerQueue() { return consumerThreadsPerQueue; }
    public void setConsumerThreadsPerQueue(int consumerThreadsPerQueue) { this.consumerThreadsPerQueue = consumerThreadsPerQueue; }
    public String getConsumerMode() { return consumerMode; }
    public void setConsumerMode(String consumerMode) { this.consumerMode = consumerMode; }
    public int getRetryInterval() { return retryInterval; } 
    public void setRetryInterval(int retryInterval) { this.retryInterval = retryInterval; }
    public double getRetryIntervalMultiplier() { return retryIntervalMultiplier; } 
    public void setRetryIntervalMultiplier(double retryIntervalMultiplier) { this.retryIntervalMultiplier = retryIntervalMultiplier; }
    public int getMaxRetryInterval() { return maxRetryInterval; } 
    public void setMaxRetryInterval(int maxRetryInterval) { this.maxRetryInterval = maxRetryInterval; }
    public int getReconnectAttempts() { return reconnectAttempts; } 
    public void setReconnectAttempts(int reconnectAttempts) { this.reconnectAttempts = reconnectAttempts; }
    public int getConfirmationWindowSize() { return confirmationWindowSize; } 
    public void setConfirmationWindowSize(int confirmationWindowSize) { this.confirmationWindowSize = confirmationWindowSize; }
    public List<String> getQueues() { return queues; }
    public void setQueues(List<String> queues) { this.queues = queues; }
}
