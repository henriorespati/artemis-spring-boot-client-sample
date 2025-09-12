# Artemis Spring Boot Client Sample

This project demonstrates a Spring Boot client for **Apache ActiveMQ Artemis** using JMS, including synchronous, request/reply, and transactional message patterns.

---

## Features

- **Synchronous production / consumption** of messages
- **Request/Reply pattern** with temporary reply queues
- **Transactional send** using JMS transactions
- **Configurable via `application.yml`**
- **Thread-safe sync consumer pool**

---

## Prerequisites

- Java 17+
- Maven 3.8+
- Apache ActiveMQ Artemis broker running (or a cluster)
- Optional: SSL truststore if using SSL

---

## Setup

1. Clone the repository:

```bash
git clone https://github.com/henriorespati/artemis-spring-boot-client-sample.git
cd artemis-spring-boot-client-sample
````

2. Build the project:

```bash
mvn clean package
```

3. Configure `application.yml` with your Artemis broker URLs, credentials, and queue names.

---

## Configuration

`application.yml` sample:

```yaml
spring:
  artemis:
    user: admin
    password: password
    broker-url: tcp://localhost:61617,tcp://localhost:61717

app:
  queue:
    request: requestQueue
    sync: syncQueue
    transaction: transactionQueue

logging:
  level:
    root: info
    com.example.artemis: debug
```

---

## Running the Application

Run with Maven:

```bash
mvn spring-boot:run
```

Or run the packaged JAR:

```bash
java -jar target/artemis-spring-boot-client-sample-1.0.0.jar
```

---

## Endpoints

| Method | URL                         | Description                                | Body                   |
| ------ | --------------------------- | ------------------------------------------ | ---------------------- |
| POST   | `/artemis/send/sync`        | Send a synchronous message                 | Raw text message       |
| POST   | `/artemis/send/request`     | Send a request message and receive a reply | Raw text message       |
| POST   | `/artemis/send/transaction` | Send multiple messages in a transaction    | JSON array of messages |

### Example cURL

**Send synchronous message:**

```bash
curl -X POST -H "Content-Type: text/plain" \
    -d "Hello Sync" http://localhost:8080/artemis/send/sync
```

**Send request/reply message:**

```bash
curl -X POST -H "Content-Type: text/plain" \
    -d "Request Message" http://localhost:8080/artemis/send/request
```

**Send transactional messages:**

```bash
curl -X POST -H "Content-Type: application/json" \
    -d '["Msg1", "Msg2", "Msg3"]' http://localhost:8080/artemis/send/transaction
```

---
