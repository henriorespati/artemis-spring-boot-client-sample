package com.example.artemis.config;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.artemis.jms.pool.ConsumerPool;
import com.example.artemis.jms.pool.ProducerPool;

import jakarta.jms.ConnectionFactory;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class ArtemisJmsConfig {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisJmsConfig.class);
    private final ArtemisProperties props;

    public ArtemisJmsConfig(ArtemisProperties props) {
        this.props = props;
    }

    // --- Pooled ConnectionFactories
    @Bean(name = "syncConnectionFactory", destroyMethod = "stop")
    public ConnectionFactory syncConnectionFactory() {
        return buildPooledConnectionFactory(true);
    }

    @Bean(name = "asyncConnectionFactory", destroyMethod = "stop")
    public ConnectionFactory asyncConnectionFactory() {
        return buildPooledConnectionFactory(false);
    }

    private ConnectionFactory buildPooledConnectionFactory(boolean sync) {
        // Build Artemis core factory
        ActiveMQConnectionFactory amqCf = new ActiveMQConnectionFactory(buildBrokerUrl());

        // username/password
        if (props.getUser() != null && !props.getUser().isEmpty()) {
            amqCf.setUser(props.getUser());
            amqCf.setPassword(props.getPassword());
        }

        // retry/reconnect settings
        amqCf.setRetryInterval(props.getRetryInterval());
        amqCf.setRetryIntervalMultiplier(props.getRetryIntervalMultiplier());
        amqCf.setMaxRetryInterval(props.getMaxRetryInterval());
        amqCf.setReconnectAttempts(props.getReconnectAttempts());

        // confirmation window
        amqCf.setConfirmationWindowSize(props.getConfirmationWindowSize());

        // sync vs async sends
        if (sync) {
            amqCf.setBlockOnDurableSend(true);
            amqCf.setBlockOnNonDurableSend(true);
        } else {
            amqCf.setBlockOnDurableSend(false);
            amqCf.setBlockOnNonDurableSend(false);
        }

        // Wrap in pooled connection factory
        JmsPoolConnectionFactory pooled = new JmsPoolConnectionFactory();
        pooled.setConnectionFactory(amqCf);
        pooled.setMaxConnections(props.getMaxConnections()); 
        pooled.setMaxSessionsPerConnection(props.getMaxSessionsPerConnection()); 
        pooled.setBlockIfSessionPoolIsFull(true);
        pooled.setBlockIfSessionPoolIsFullTimeout(5000);
        pooled.setUseAnonymousProducers(false); 

        logger.info("Created {} pooled Artemis ConnectionFactory (sync={})", 
                    pooled.getClass().getSimpleName(), sync);
        return (ConnectionFactory) pooled;
    }

    private String buildBrokerUrl() {
        List<String> urls = new ArrayList<>();
        for (String broker : props.getBrokerUrls()) {
            if (props.getSslEnabled() &&
                props.getTrustStorePath() != null &&
                !props.getTrustStorePath().isEmpty()) {
                broker += "?sslEnabled=true" +
                          "&trustStorePath=" + props.getTrustStorePath() +
                          "&trustStorePassword=" + props.getTrustStorePassword() +
                          "&verifyHost=false";
            }
            urls.add(broker);
        }
        String url = String.join(",", urls);
        logger.info("Connecting to Artemis broker(s) at: {}", url);
        return url;
    }

    // --- ProducerPools
    @Bean(name = "syncProducerPool")
    public ProducerPool syncProducerPool(@Qualifier("syncConnectionFactory") ConnectionFactory cf) {
        return new ProducerPool(cf, props);
    }

    @Bean(name = "asyncProducerPool")
    public ProducerPool asyncProducerPool(@Qualifier("asyncConnectionFactory") ConnectionFactory cf) {
        return new ProducerPool(cf, props);
    }

    // --- ConsumerPool
    @Bean(initMethod = "start", destroyMethod = "stop")
    public ConsumerPool consumerPool(
            @Qualifier("syncConnectionFactory") ConnectionFactory syncCF,
            @Qualifier("asyncConnectionFactory") ConnectionFactory asyncCF) {

        if ("SYNC".equalsIgnoreCase(props.getConsumerMode())) {
            logger.info("Using SYNC ConsumerPool");
            return new ConsumerPool(syncCF, props);
        } else {
            logger.info("Using ASYNC ConsumerPool");
            return new ConsumerPool(asyncCF, props);
        }
    }
}
