package com.example.artemis.config;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
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

    // --- ConnectionFactories
    @Bean(name = "syncConnectionFactory")
    public ConnectionFactory syncConnectionFactory() {
        return buildConnectionFactory(true);
    }

    @Bean(name = "asyncConnectionFactory")
    public ConnectionFactory asyncConnectionFactory() {
        return buildConnectionFactory(false);
    }

    private ConnectionFactory buildConnectionFactory(boolean sync) {
        // Prepare broker URLs with SSL parameters if needed
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

        ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory(url);

        // username/password
        if (props.getUser() != null && !props.getUser().isEmpty()) {
            cf.setUser(props.getUser());
            cf.setPassword(props.getPassword());
        }

        // retry/reconnect settings
        cf.setRetryInterval(props.getRetryInterval());
        cf.setRetryIntervalMultiplier(props.getRetryIntervalMultiplier());
        cf.setMaxRetryInterval(props.getMaxRetryInterval());
        cf.setReconnectAttempts(props.getReconnectAttempts());

        // confirmation window
        cf.setConfirmationWindowSize(props.getConfirmationWindowSize());

        // sync vs async sends
        if (sync) {
            cf.setBlockOnDurableSend(true);
            cf.setBlockOnNonDurableSend(true);
        } else {
            cf.setBlockOnDurableSend(false);
            cf.setBlockOnNonDurableSend(false);
        }

        return cf;
    }

    // --- ProducerPools
    @Bean(name = "syncProducerPool", initMethod = "init", destroyMethod = "destroy")
    public ProducerPool syncProducerPool(@Qualifier("syncConnectionFactory") ConnectionFactory cf) {
        return new ProducerPool(cf, props);
    }

    @Bean(name = "asyncProducerPool", initMethod = "init", destroyMethod = "destroy")
    public ProducerPool asyncProducerPool(@Qualifier("asyncConnectionFactory") ConnectionFactory cf) {
        return new ProducerPool(cf, props);
    }

    // --- ConsumerPool
    @Bean(initMethod = "start", destroyMethod = "stop")
    public ConsumerPool consumerPool(
            @Qualifier("syncConnectionFactory") ConnectionFactory syncCF,
            @Qualifier("asyncConnectionFactory") ConnectionFactory asyncCF) {

        if ("SYNC".equalsIgnoreCase(props.getConsumerMode())) {
            return new ConsumerPool(syncCF, props);
        } else {
            return new ConsumerPool(asyncCF, props);
        }
    }
}
