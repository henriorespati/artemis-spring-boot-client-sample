# Artemis Spring Boot Client Sample

This project demonstrates how to integrate **Apache ActiveMQ Artemis** with a **Spring Boot 3** application using the **Jakarta JMS 3.1 API**.  
It provides REST endpoints for sending messages to Artemis queues in different modes: synchronous, asynchronous, request/reply, and transactional (single and batch).

---

## Features

- Spring Boot 3.0.x with **Java 17**  
- JMS client for **Apache ActiveMQ Artemis 2.33.0**  
- Connection pooling with **Pooled JMS** and **Commons Pool2**  
- REST endpoints for:
  - Sync vs. Async sends
  - Request/Reply
  - Transactional sends (single and batch)  
- Configurable Artemis client parameters via `application.yml`  
- Example **confirmation-window-size** tuning for async sends

---

## Prerequisites

- JDK 17+  
- Maven 3.8+  
- Running instance of **Apache ActiveMQ Artemis** (standalone or via Docker)

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/henriorespati/artemis-spring-boot-client-sample.git
cd artemis-spring-boot-client-sample
```

### 2. Build the project
```bash
mvn clean package
```

### 3. Run the application
```bash
mvn spring-boot:run
```

or with the packaged JAR:
```bash
java -jar target/artemis-jakarta-client-0.0.1-SNAPSHOT.jar
```

The app starts on port 8080 by default.

## Configuration

Put your runtime configuration in src/main/resources/application.yml (or .properties). Below is the example configuration used by this sample:

```yaml
server:
  port: 8080

spring:
  artemis:
    # Artemis broker connection
    broker-url: (tcp://localhost:61616,tcp://localhost:61716)?useTopologyForLoadBalancing=true&sslEnabled=true&trustStoreType=PKCS12&trustStorePath=truststore.p12&trustStorePassword=changeit
    user: admin
    password: password
    mode: native                           # default: "embedded" (switch to "native" if broker-url is set)

    pool:
      enabled: true                        # default false (pooling disabled by default)
      max-connections: 1                   # default 1
      max-sessions-per-connection: 500     # default 500

### Optional parameters for Spring Boot JMS auto configuration ###
      block-if-full: true                  # default true
      block-if-full-timeout: -1ms          # default -1 (indefinite wait)
      idle-timeout: 30s                    # default 30s      
      time-between-expiration-check: -1ms  # default -1 (disabled)
      use-anonymous-producers: true        # default true

  jms:
    # Caching / session settings
    cache:
      enabled: true                        # default true
      session-cache-size: 1                # default 1 (how many sessions cached per connection)

    # JMS Listener container settings
    listener:
      acknowledge-mode: auto               # options: auto, client, dups_ok
      auto-startup: true
      # concurrency: "1-1"                 # optionally set concurrency
      receive-timeout: 1000                # ms

    template:
      default-destination:
      delivery-mode: persistent
      priority: 4
      qos-enabled: false                   # set true to enable deliveryMode/priority/ttl
      receive-timeout: 1000
      time-to-live: 0

    pub-sub-domain: false                  # false=Queues, true=Topics
### End of optional parameters ###

app:
  artemis:
    queues:
      - exampleQueue1
      - exampleQueue2
    confirmation-window-size: 1048576      # required for async sends (default -1 = disabled)
    consumer:
      mode: TX                             # SYNC, ASYNC, REPLY, TX
      threads-per-queue: 2

logging:
  level:
    root: info
    com.example.artemis: debug
```

## Important config notes

``broker-url`` can contain multiple endpoints and query params. The sample uses ``useTopologyForLoadBalancing=true`` for multi-broker setups.

``spring.artemis.pool.*`` controls the pooled JMS behavior (connections/sessions). Tune ``max-connections`` and ``max-sessions-per-connection`` for your workload.

``app.artemis.confirmation-window-size`` is important for async sends — set it to a positive value if you use async producers.

``spring.jms.template.qos-enabled`` must be true if you want ``delivery-mode``, ``priority``, and ``time-to-live`` enforced.

## REST Endpoints

All endpoints are rooted under ``/artemis``. Replace ``{queueName}`` with the target queue.

The controller in this sample exposes these routes:

``POST /artemis/send/sync/{queueName}`` — synchronous send

``POST /artemis/send/async/{queueName}`` — asynchronous send

``POST /artemis/send/sync/request/{queueName}`` — synchronous request/reply (send and wait)

``POST /artemis/send/async/request/{queueName}`` — asynchronous request/reply

``POST /artemis/send/tx/{queueName}`` — transactional single send

``POST /artemis/send/tx/batch/{queueName}`` — transactional batch send (JSON array)

### Send Sync
```bash
curl -X POST "http://localhost:8080/artemis/send/sync/exampleQueue1" \
     -H "Content-Type: text/plain" \
     -d "Hello Sync"
```

### Send Async
```bash
curl -X POST "http://localhost:8080/artemis/send/async/exampleQueue1" \
     -H "Content-Type: text/plain" \
     -d "Hello Async"
```

### Request/Reply (Sync)
```bash
curl -X POST "http://localhost:8080/artemis/send/sync/request/exampleQueue1" \
     -H "Content-Type: text/plain" \
     -d "Request Message"
```

### Request/Reply (Async)
```bash
curl -X POST "http://localhost:8080/artemis/send/async/request/exampleQueue1" \
     -H "Content-Type: text/plain" \
     -d "Request Message"
```

### Transactional Send
```bash
curl -X POST "http://localhost:8080/artemis/send/tx/exampleQueue1" \
     -H "Content-Type: text/plain" \
     -d "Transactional Message"
```

### Transactional Batch Send

Sends multiple messages within a single transaction. The request body should be a JSON array of strings:
```bash
curl -X POST "http://localhost:8080/artemis/send/tx/batch/exampleQueue1" \
     -H "Content-Type: application/json" \
     -d '["Msg1","Msg2","Msg3"]'
```


## Dependencies (high-level)

Key dependencies are listed in ``pom.xml``:

``org.apache.activemq:artemis-jakarta-client`` — Artemis JMS client

``org.apache.activemq:artemis-core-client`` — Artemis transport/core

``org.messaginghub:pooled-jms`` — connection pooling for JMS

``org.springframework:spring-jms`` — Spring JMS integration

``jakarta.jms:jakarta.jms-api`` — JMS 3.1 API


## Troubleshooting & Tips

If async sends fail or behave unexpectedly, verify ``confirmation-window-size`` in ``app.artemis`` and tune pool/session settings.

For heavy producer workloads increase ``max-sessions-per-connection`` or ``max-connections`` while monitoring broker resources.

Use Artemis web console (port 8161) to inspect queues, consumers, and messages.

Enable ``com.example.artemis: debug`` in ``logging.level`` to see detailed client-side logs while developing.