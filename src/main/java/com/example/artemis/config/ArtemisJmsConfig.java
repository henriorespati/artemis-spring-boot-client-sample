package com.example.artemis.config;

import com.example.artemis.jms.pool.ConsumerPool;
import com.example.artemis.jms.pool.ProducerPool;
import jakarta.jms.ConnectionFactory;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ArtemisJmsConfig {

    private static final Logger logger = LoggerFactory.getLogger(ArtemisJmsConfig.class);

    private final ArtemisProperties appProps;

    public ArtemisJmsConfig(ArtemisProperties appProps) {
        this.appProps = appProps;
    }

    @Bean
    public ConnectionFactory pooledConnectionFactory(
        @Value("${spring.artemis.broker-url}") String brokerUrl,
        @Value("${spring.artemis.user}") String user,
        @Value("${spring.artemis.password}") String password,
        @Value("${spring.artemis.pool.max-connections}") int maxConnections,
        @Value("${spring.artemis.pool.max-sessions-per-connection}") int maxSessionsPerConnection) {

        ActiveMQConnectionFactory amqCf = new ActiveMQConnectionFactory(brokerUrl);
        amqCf.setUser(user);
        amqCf.setPassword(password);
        amqCf.setConfirmationWindowSize(appProps.getConfirmationWindowSize());

        JmsPoolConnectionFactory pooled = new JmsPoolConnectionFactory();
        pooled.setConnectionFactory(amqCf);
        pooled.setMaxConnections(maxConnections);
        pooled.setMaxSessionsPerConnection(maxSessionsPerConnection);
        pooled.setUseAnonymousProducers(true);

        return pooled;
    }


    @Bean
    public ProducerPool producerPool(ConnectionFactory connectionFactory) {
        logger.info("Creating ProducerPool for queues {}", appProps.getQueues());
        return new ProducerPool(connectionFactory, appProps.getQueues());
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public ConsumerPool consumerPool(ConnectionFactory connectionFactory) {
        ConsumerPool.Mode mode = "ASYNC".equalsIgnoreCase(appProps.getConsumer().getMode())
                ? ConsumerPool.Mode.ASYNC
                : ConsumerPool.Mode.SYNC;

        logger.info("Creating ConsumerPool with mode={} and threadsPerQueue={}",
                mode, appProps.getConsumer().getThreadsPerQueue());

        return new ConsumerPool(
                connectionFactory,
                appProps.getQueues(),
                appProps.getConsumer().getThreadsPerQueue(),
                mode
        );
    }

}
