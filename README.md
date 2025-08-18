# Artemis Core API Spring Boot Client with Pooled Producers & Consumers

This is a **Spring Boot** application demonstrating best practices for **Apache ActiveMQ Artemis Core API** client usage:
- **Connection pooling** for producers and consumers
- **Configurable multiple queues** via `application.yml`
- **Sync & async send** with Core API async acknowledgements
- **Clustered broker** connection support
- **Event-driven consumer listeners** using Artemis Core API
- **Thread-pool based scaling** for both producers and consumers

---

## Features

1. **Config-driven multiple queue setup**
   - Add/remove queues via `application.yml` without code changes.

2. **Producer Pooling**
   - Avoids repeated `ClientSession`/`ClientProducer` creation.
   - Configurable pool size per queue.

3. **Consumer Pooling**
   - Multiple concurrent consumers per queue.
   - Event-driven listener model for message processing.

4. **Cluster Support**
   - Configure multiple broker URLs for failover.

6. **Sync vs Async Send**
   - Sync: `producer.send(message)` blocks until acknowledged.
   - Async: `producer.send(message, SendAcknowledgementHandler)` returns immediately; callback invoked on ack/fail.

---

## Project Structure
```
src/main/java/com/example/artemis
│
├── config/ # Configuration properties mapping
├── core/pool/ # Producer & Consumer pool implementations
├── listener/ # Event-driven message listener
├── service/ # Message sending service
├── controller/ # REST endpoints for send operations
└── ArtemisCoreApp.java # Spring Boot main application
```
---

## REST API Endpoints

### Send Message (Sync)

POST `/artemis/send/sync/{queueName}`

Body: raw text message


- Waits for broker acknowledgment before returning.

### Send Message (Async)

POST `/artemis/send/async/{queueName}`

Body: raw text message


- Returns immediately, broker ack handled in background via `SendAcknowledgementHandler`.

---

## Example `application.yml`

```yaml
server:
  port: 8080

artemis:
  core:
    broker-urls:
      - ssl://127.0.0.1:61617
      - ssl://127.0.0.1:61717
    user: admin
    password: password
    trust-store-path: truststore.p12
    trust-store-password: changeit
    producer-pool-size: 2
    consumer-threads-per-queue: 1
    consumer-mode: SYNC
    retry-interval: 1000
    retry-interval-multiplier: 1.5
    max-retry-interval: 5000
    reconnect-attempts: 5
    confirmation-window-size: 1024
    queues:
      - exampleQueue1
      - exampleQueue2

logging:
  level:
    root: info
    com.example.artemis: debug
```

## Requirements
- Java 17+

- Maven 3.8+

- Running Apache ActiveMQ Artemis cluster or standalone broker

## How to Run

### 1. Build
```
mvn clean package
```

### 2. Run
```
mvn spring-boot:run
```

## How to Test

Send sync message

```
curl -X POST http://localhost:8080/artemis/send/sync/exampleQueue \
     -H "Content-Type: text/plain" \
     -d "Hello Artemis Sync!"
```

Send async message

```
curl -X POST http://localhost:8080/artemis/send/async/exampleQueue \
     -H "Content-Type: text/plain" \
     -d "Hello Artemis Async!"
```




---