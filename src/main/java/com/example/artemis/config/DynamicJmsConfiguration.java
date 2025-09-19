package com.example.artemis.config;

import com.example.artemis.listener.ArtemisListener;

import jakarta.jms.TextMessage;

import java.util.Random;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.messaginghub.pooled.jms.JmsPoolConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerEndpointRegistrar;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.retry.annotation.EnableRetry;

@Configuration(proxyBeanMethods = false)
@EnableJms
@EnableRetry
@Profile("multi-brokers")
public class DynamicJmsConfiguration implements BeanDefinitionRegistryPostProcessor,
        EnvironmentAware, JmsListenerConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(DynamicJmsConfiguration.class);

    private Environment environment;
    private BeanFactory beanFactory;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    /** Normalize broker URLs so all have same parameters */
    private String[] normalizeBrokerUrls(String brokerUrlsProperty) {
        String[] parts = brokerUrlsProperty.split(",");
        if (parts.length == 0) return new String[0];

        String baseParams = "";
        // Look for params in any of the URLs
        for (String url : parts) {
            int idx = url.indexOf('?');
            if (idx != -1) {
                baseParams = url.substring(idx); // take everything after "?"
                break;
            }
        }

        // If no URL has params, leave it empty
        String[] normalized = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String baseUrl = parts[i].trim();
            int idx = baseUrl.indexOf('?');
            if (idx != -1) {
                baseUrl = baseUrl.substring(0, idx); // strip existing params
            }
            normalized[i] = baseUrl + baseParams;
        }

        logger.info("Normalized broker URLs: {}", String.join(", ", normalized));
        return normalized;
    }

    /** Dynamic JMS listener factory wrapper */
    @Bean
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory() {
        String brokerUrlsProperty = environment.getProperty("spring.artemis.broker-url");
        if (brokerUrlsProperty == null || brokerUrlsProperty.isEmpty()) {
            throw new IllegalStateException("No brokers configured for dynamic JMS listener");
        }
        String[] urls = normalizeBrokerUrls(brokerUrlsProperty);
        int index = new Random().nextInt(urls.length);
        String brokerId = "broker" + (index + 1);
        String factoryBeanName = brokerId + "JmsListenerContainerFactory";
        return (DefaultJmsListenerContainerFactory) beanFactory.getBean(factoryBeanName);
    }

    /** Dynamically register broker beans */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        String brokerUrlsProperty = environment.getProperty("spring.artemis.broker-url");
        String user = environment.getProperty("spring.artemis.user");
        String password = environment.getProperty("spring.artemis.password");
        boolean transacted = Boolean.parseBoolean(
                environment.getProperty("spring.jms.listener.session.transacted", "true")
        );

        if (brokerUrlsProperty != null && !brokerUrlsProperty.isEmpty()) {
            String[] urls = normalizeBrokerUrls(brokerUrlsProperty);

            for (int i = 0; i < urls.length; i++) {
                String normalizedUrl = urls[i];
                String brokerId = "broker" + (i + 1);

                // ActiveMQConnectionFactory
                String amqCfBeanName = brokerId + "AmqConnectionFactory";
                BeanDefinitionBuilder amqCfBuilder = BeanDefinitionBuilder
                        .genericBeanDefinition(ActiveMQConnectionFactory.class)
                        .addConstructorArgValue(normalizedUrl);
                if (user != null) amqCfBuilder.addPropertyValue("user", user);
                if (password != null) amqCfBuilder.addPropertyValue("password", password);
                registry.registerBeanDefinition(amqCfBeanName, amqCfBuilder.getBeanDefinition());

                // JmsPoolConnectionFactory
                String pooledCfBeanName = brokerId + "ConnectionFactory";
                BeanDefinitionBuilder pooledCfBuilder = BeanDefinitionBuilder
                        .genericBeanDefinition(JmsPoolConnectionFactory.class)
                        .addPropertyReference("connectionFactory", amqCfBeanName)
                        .addPropertyValue("maxConnections", environment.getProperty("spring.artemis.pool.max-connections"))
                        .addPropertyValue("connectionIdleTimeout", environment.getProperty("spring.artemis.pool.idle-timeout"))
                        .addPropertyValue("blockIfSessionPoolIsFull", environment.getProperty("spring.artemis.pool.block-if-full"))
                        .addPropertyValue("blockIfSessionPoolIsFullTimeout", environment.getProperty("spring.artemis.pool.block-if-full-timeout"));
                registry.registerBeanDefinition(pooledCfBeanName, pooledCfBuilder.getBeanDefinition());

                // Listener Container Factory
                String factoryBeanName = brokerId + "JmsListenerContainerFactory";
                BeanDefinitionBuilder factoryBuilder = BeanDefinitionBuilder
                        .genericBeanDefinition(DefaultJmsListenerContainerFactory.class)
                        .addPropertyReference("connectionFactory", pooledCfBeanName)
                        .addPropertyValue("sessionTransacted", transacted)
                        .addPropertyValue("pubSubDomain", false);
                registry.registerBeanDefinition(factoryBeanName, factoryBuilder.getBeanDefinition());

                // JmsTemplate
                String templateBeanName = brokerId + "JmsTemplate";
                BeanDefinitionBuilder templateBuilder = BeanDefinitionBuilder
                        .genericBeanDefinition(JmsTemplate.class)
                        .addPropertyReference("connectionFactory", pooledCfBeanName)
                        .addPropertyValue("receiveTimeout", 5000);
                registry.registerBeanDefinition(templateBeanName, templateBuilder.getBeanDefinition());

                logger.info("Registered dynamic beans for broker {}: {}, {}, {}",
                        normalizedUrl, pooledCfBeanName, factoryBeanName, templateBeanName);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    /** Hook into Spring JMS listener registration */
    @Override
    public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) throws BeansException {
        String brokerUrlsProperty = environment.getProperty("spring.artemis.broker-url");
        String queueName = environment.getProperty("app.queue.sync");

        if (brokerUrlsProperty != null && !brokerUrlsProperty.isEmpty()) {
            String[] urls = normalizeBrokerUrls(brokerUrlsProperty);
            int minConcurrency = Integer.parseInt(environment.getProperty("spring.jms.listener.min-concurrency", "1"));
            int maxConcurrency = Integer.parseInt(environment.getProperty("spring.jms.listener.max-concurrency", "1"));

            for (int i = 0; i < urls.length; i++) {
                String brokerId = "broker" + (i + 1);
                String factoryBeanName = brokerId + "JmsListenerContainerFactory";

                DefaultJmsListenerContainerFactory factory =
                        (DefaultJmsListenerContainerFactory) beanFactory.getBean(factoryBeanName);
                factory.setConcurrency(minConcurrency + "-" + maxConcurrency);
                factory.setClientId("listener-" + brokerId); // optional but recommended for durable subscriptions

                // Create a listener endpoint for each broker
                SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
                endpoint.setId("dynamicListener-" + brokerId);
                endpoint.setDestination(queueName);
                endpoint.setMessageListener(message -> {
                    ArtemisListener artemisListener = beanFactory.getBean(ArtemisListener.class);
                    if (message instanceof TextMessage textMessage) {
                        try {
                            // artemisListener.receiveSync(textMessage);
                        } catch (Exception e) {
                            logger.error("Error processing message from {}: {}", brokerId, e.getMessage(), e);
                            throw new RuntimeException(e); // trigger retry
                        }
                    } else {
                        logger.warn("Unsupported message type: {}", message.getClass());
                    }
                });

                registrar.registerEndpoint(endpoint, factory);
                logger.info("Registered listener endpoint {} for broker {} at {}", endpoint.getId(), brokerId, urls[i]);
            }
        }
    }

    /** Log dynamic broker config */
    @Bean
    public CommandLineRunner logDynamicBrokerConfig() {
        return args -> {
            String brokerUrlsProperty = environment.getProperty("spring.artemis.broker-url");
            if (brokerUrlsProperty != null && !brokerUrlsProperty.isEmpty()) {
                String[] urls = normalizeBrokerUrls(brokerUrlsProperty);

                for (int i = 0; i < urls.length; i++) {
                    String brokerId = "broker" + (i + 1);
                    String pooledCfBeanName = brokerId + "ConnectionFactory";

                    JmsPoolConnectionFactory pool =
                            (JmsPoolConnectionFactory) beanFactory.getBean(pooledCfBeanName);

                    logger.info("---- JMS CONFIG [{}] ----", brokerId);
                    logger.info("Broker URL: {}", urls[i]);
                    logger.info("ConnectionFactory type: {}", pool.getClass().getName());
                    logger.info("Pool enabled: maxConnections={}, idleTimeout(ms)={}, blockIfFull={}, blockIfFullTimeout(ms)={}",
                            pool.getMaxConnections(),
                            pool.getConnectionIdleTimeout(),
                            pool.isBlockIfSessionPoolIsFull(),
                            pool.getBlockIfSessionPoolIsFullTimeout()
                    );
                    logger.info("NumConnections in use: {}", pool.getNumConnections());

                    if (pool.getConnectionFactory() instanceof ActiveMQConnectionFactory amq) {
                        var locator = amq.getServerLocator();
                        logger.info("ActiveMQ Artemis connection settings:");
                        logger.info("  reconnectAttempts={} retryInterval={} retryIntervalMultiplier={} maxRetryInterval={}",
                                locator.getReconnectAttempts(),
                                locator.getRetryInterval(),
                                locator.getRetryIntervalMultiplier(),
                                locator.getMaxRetryInterval()
                        );
                        logger.info("  connectionTtl={} callTimeout={}",
                                locator.getConnectionTTL(),
                                locator.getCallTimeout()
                        );
                        logger.info("  confirmationWindowSize={} producerWindowSize={} consumerWindowSize={}",
                                locator.getConfirmationWindowSize(),                                
                                locator.getProducerWindowSize(),
                                locator.getConsumerWindowSize()
                        );
                        logger.info("  blockOnDurableSend={} blockOnAcknowledge={}",
                                locator.isBlockOnDurableSend(),
                                locator.isBlockOnAcknowledge()
                        );                        
                        logger.info("  useTopologyForLoadBalancing={}",
                                locator.getUseTopologyForLoadBalancing()
                        );
                        logger.info("  threadPoolMaxSize={} scheduledThreadPoolMaxSize={}",
                                locator.getThreadPoolMaxSize(),
                                locator.getScheduledThreadPoolMaxSize()
                        );
                    }
                    logger.info("---- END CONFIG [{}] ----", brokerId);
                }
            }
        };
    }
}
