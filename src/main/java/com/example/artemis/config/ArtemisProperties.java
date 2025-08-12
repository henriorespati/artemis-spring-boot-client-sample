package com.example.artemis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "artemis")
public class ArtemisProperties {

    private List<String> brokerUrls;
    private Ssl ssl;
    private List<QueueConfig> queues;
    private Pool pool;

    public List<String> getBrokerUrls() {
        return brokerUrls;
    }

    public void setBrokerUrls(List<String> brokerUrls) {
        this.brokerUrls = brokerUrls;
    }

    public Ssl getSsl() {
        return ssl;
    }

    public void setSsl(Ssl ssl) {
        this.ssl = ssl;
    }

    public List<QueueConfig> getQueues() {
        return queues;
    }

    public void setQueues(List<QueueConfig> queues) {
        this.queues = queues;
    }

    public Pool getPool() {
        return pool;
    }

    public void setPool(Pool pool) {
        this.pool = pool;
    }

    public static class Ssl {
        private boolean enabled;
        private String trustStorePath;
        private String trustStorePassword;
        private String keyStorePath;
        private String keyStorePassword;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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

        public String getKeyStorePath() {
            return keyStorePath;
        }

        public void setKeyStorePath(String keyStorePath) {
            this.keyStorePath = keyStorePath;
        }

        public String getKeyStorePassword() {
            return keyStorePassword;
        }

        public void setKeyStorePassword(String keyStorePassword) {
            this.keyStorePassword = keyStorePassword;
        }
    }

    public static class QueueConfig {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Pool {
        private int maxProducers;
        private int maxConsumers;
        private long consumerReceiveTimeoutMs;

        public int getMaxProducers() {
            return maxProducers;
        }

        public void setMaxProducers(int maxProducers) {
            this.maxProducers = maxProducers;
        }

        public int getMaxConsumers() {
            return maxConsumers;
        }

        public void setMaxConsumers(int maxConsumers) {
            this.maxConsumers = maxConsumers;
        }

        public long getConsumerReceiveTimeoutMs() {
            return consumerReceiveTimeoutMs;
        }

        public void setConsumerReceiveTimeoutMs(long consumerReceiveTimeoutMs) {
            this.consumerReceiveTimeoutMs = consumerReceiveTimeoutMs;
        }
    }
}
