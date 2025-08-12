# Artemis Advanced Demo

This project demonstrates a **Spring Boot** application using the **Apache ActiveMQ Artemis Core API** with:

- Connection pooling for producers and consumers  
- Support for multiple queues configured via `application.yml`  
- Dedicated synchronous and asynchronous send and receive REST endpoints  
- SSL/TLS support (configurable)  
- A sample JMeter test plan for load testing the REST API  

---

## Features

- Efficient reuse of JMS connections, sessions, producers, and consumers  
- Configurable queues and broker URLs  
- Separate sync and async send/receive using Spring's `@Async` and `CompletableFuture`  
- SSL parameters to secure connections  
- Simple REST endpoints for integration testing or usage  
- Load testing with JMeter included  

---

## Requirements

- Java 17+  
- Maven 3.6+  
- Apache Artemis broker running (default assumes localhost:61616)  
- (Optional) SSL keystore and truststore files if SSL is enabled  

---

## Setup

1. Clone this repository  

2. Configure `src/main/resources/application.yml` to match your Artemis broker:

```yaml
artemis:
  brokerUrls:
    - tcp://localhost:61616
  ssl:
    enabled: false
    trustStorePath: classpath:ssl/truststore.jks
    trustStorePassword: changeit
    keyStorePath: classpath:ssl/keystore.jks
    keyStorePassword: changeit
  queues:
    - name: queue.orders
    - name: queue.payments
  pool:
    maxProducers: 10
    maxConsumers: 10
    consumerReceiveTimeoutMs: 1000
