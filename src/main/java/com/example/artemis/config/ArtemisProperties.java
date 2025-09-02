package com.example.artemis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "artemis.jms")
public class ArtemisProperties {

    // --- Connection settings ---
    private List<String> brokerUrls;          // e.g. [ "ssl://127.0.0.1:61617" ]
    private String user;
    private String password;

    // --- SSL (optional) ---
    private boolean sslEnabled = false;
    private String trustStorePath = "";
    private String trustStorePassword = "";

    // --- Producer / Consumer ---
    private int producerPoolSize = 1;
    private int consumerThreadsPerQueue = 1;
    private String consumerMode = "ASYNC";    // SYNC or ASYNC
    private List<String> queues;

    // --- Retry / reconnect ---
    private long retryInterval = 2000;
    private double retryIntervalMultiplier = 2.0;
    private long maxRetryInterval = 60000;
    private int reconnectAttempts = -1;

    // --- Advanced Artemis tuning ---
    private int confirmationWindowSize = 1048576; // 1 MB default

    // --- Getters & Setters ---
    public List<String> getBrokerUrls() {
        return brokerUrls;
    }
    public void setBrokerUrls(List<String> brokerUrls) {
        this.brokerUrls = brokerUrls;
    }

    public String getUser() {
        return user;
    }
    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }

    public boolean getSslEnabled() {
        return sslEnabled;
    }
    public void setSslEnabled(boolean sslEnabled) {
        this.sslEnabled = sslEnabled;
    }

    public String getTrustStorePath() {
        return trustStorePath;
    }
    public void setTrustStorePath(String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }
    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public int getProducerPoolSize() {
        return producerPoolSize;
    }
    public void setProducerPoolSize(int producerPoolSize) {
        this.producerPoolSize = producerPoolSize;
    }

    public int getConsumerThreadsPerQueue() {
        return consumerThreadsPerQueue;
    }
    public void setConsumerThreadsPerQueue(int consumerThreadsPerQueue) {
        this.consumerThreadsPerQueue = consumerThreadsPerQueue;
    }

    public String getConsumerMode() {
        return consumerMode;
    }
    public void setConsumerMode(String consumerMode) {
        this.consumerMode = consumerMode;
    }

    public List<String> getQueues() {
        return queues;
    }
    public void setQueues(List<String> queues) {
        this.queues = queues;
    }

    public long getRetryInterval() {
        return retryInterval;
    }
    public void setRetryInterval(long retryInterval) {
        this.retryInterval = retryInterval;
    }

    public double getRetryIntervalMultiplier() {
        return retryIntervalMultiplier;
    }
    public void setRetryIntervalMultiplier(double retryIntervalMultiplier) {
        this.retryIntervalMultiplier = retryIntervalMultiplier;
    }

    public long getMaxRetryInterval() {
        return maxRetryInterval;
    }
    public void setMaxRetryInterval(long maxRetryInterval) {
        this.maxRetryInterval = maxRetryInterval;
    }

    public int getReconnectAttempts() {
        return reconnectAttempts;
    }
    public void setReconnectAttempts(int reconnectAttempts) {
        this.reconnectAttempts = reconnectAttempts;
    }

    public int getConfirmationWindowSize() {
        return confirmationWindowSize;
    }
    public void setConfirmationWindowSize(int confirmationWindowSize) {
        this.confirmationWindowSize = confirmationWindowSize;
    }
}
