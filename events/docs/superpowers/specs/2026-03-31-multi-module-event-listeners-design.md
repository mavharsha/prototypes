# Multi-Module Event Listeners — Design Spec

## Overview

Restructure the existing `micronaut-sqs` project into a Maven multi-module project under `events/` with three independent listener modules — SQS, Kafka, and ActiveMQ Artemis. Each module produces its own Docker image suitable for K8s/GitOps/Argo deployment.

## Tech Stack

- **Java**: 21
- **Framework**: Micronaut 4.10.4
- **Build**: Maven multi-module
- **Containerization**: Multi-stage Dockerfiles per module
- **Local dev**: Single docker-compose.yml with all three brokers

## Project Structure

```
events/
├── pom.xml                              # Parent POM (packaging: pom)
├── docker-compose.yml                   # Local dev — all 3 brokers
├── listener-sqs/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── aot-jar.properties
│   └── src/main/java/com/mavharsha/events/sqs/
│       ├── Application.java
│       └── listener/SqsListenerService.java
├── listener-kafka/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── aot-jar.properties
│   └── src/main/java/com/mavharsha/events/kafka/
│       ├── Application.java
│       └── listener/KafkaListenerService.java
├── listener-activemq/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── aot-jar.properties
│   └── src/main/java/com/mavharsha/events/activemq/
│       ├── Application.java
│       └── listener/ActiveMqListenerService.java
├── infra/
│   ├── localstack/init-sqs.sh           # Creates SQS queue on startup
│   ├── kafka/                           # (no init needed — topic auto-created)
│   └── artemis/create-queue.sh          # Creates Artemis queue on startup
└── scripts/
    ├── publish-sqs.sh
    ├── publish-kafka.sh
    └── publish-activemq.sh
```

## Parent POM

- **GroupId**: `com.mavharsha`
- **ArtifactId**: `events-parent`
- **Packaging**: `pom`
- **Parent**: `io.micronaut.platform:micronaut-parent:4.10.4`
- **Modules**: `listener-sqs`, `listener-kafka`, `listener-activemq`
- **Properties**:
  - `jdk.version`: 21
  - `micronaut.version`: 4.10.4
- **Common plugins** (in `<pluginManagement>`):
  - `micronaut-maven-plugin`
  - `maven-compiler-plugin` with Micronaut annotation processors
  - `maven-enforcer-plugin`
- **Common dependency management**:
  - `micronaut-http-server-netty`
  - `micronaut-serde-jackson`
  - `snakeyaml` (runtime)
  - `logback-classic` (runtime)
  - Test dependencies (JUnit 5 + Micronaut test)

Each child module declares only its broker-specific dependencies.

## Module: listener-sqs

### Dependencies
- `io.micronaut.aws:micronaut-aws-messaging`
- `software.amazon.awssdk:sqs`

### Listener Implementation
```java
@SqsListener("events-queue")
public class SqsListenerService {

    void onMessage(@MessageBody String body, @MessageHeader("MessageId") String messageId) {
        LOG.info("Received SQS message [{}]: {}", messageId, body);
    }
}
```
- Replaces the current `@Scheduled` poll loop
- Acknowledgment and deletion handled automatically by Micronaut
- No manual `ReceiveMessageRequest` or `deleteMessage`

### Configuration (application.yml)
```yaml
micronaut:
  application:
    name: listener-sqs
aws:
  access-key-id: test
  secret-key: test
  region: us-east-1
  services:
    sqs:
      endpoint-override: http://localhost:4566
```

## Module: listener-kafka

### Dependencies
- `io.micronaut.kafka:micronaut-kafka`

### Listener Implementation
```java
@KafkaListener(groupId = "events-listener")
public class KafkaListenerService {

    @Topic("events-topic")
    void onMessage(String body) {
        LOG.info("Received Kafka message: {}", body);
    }
}
```
- Consumer group managed by Micronaut Kafka
- Offset commits handled automatically

### Configuration (application.yml)
```yaml
micronaut:
  application:
    name: listener-kafka
kafka:
  bootstrap:
    servers: localhost:9092
```

## Module: listener-activemq

### Dependencies
- `io.micronaut.jms:micronaut-jms-activemq-artemis`

### Listener Implementation
```java
@JMSListener("activemq")
public class ActiveMqListenerService {

    @Queue("events-queue")
    void onMessage(@MessageBody String body) {
        LOG.info("Received ActiveMQ message: {}", body);
    }
}
```
- Connection factory auto-configured by Micronaut JMS Artemis module
- Acknowledgment handled by the framework

### Configuration (application.yml)
```yaml
micronaut:
  application:
    name: listener-activemq
  jms:
    activemq:
      artemis:
        enabled: true
        connection-string: tcp://localhost:61616
        username: artemis
        password: artemis
```

## Dockerfiles

Each module has an identical multi-stage Dockerfile:

```dockerfile
# Build context: events/ root
# Build with: docker build -f listener-<broker>/Dockerfile -t events/listener-<broker>:latest .
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml ./pom.xml
COPY listener-<broker>/pom.xml ./listener-<broker>/pom.xml
RUN mvn -pl listener-<broker> -am dependency:resolve
COPY listener-<broker>/src ./listener-<broker>/src
RUN mvn -pl listener-<broker> -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/listener-<broker>/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- Build stage: resolves dependencies first (cache-friendly), then copies source and builds
- Runtime stage: slim JRE image, ~150MB
- Build context is the `events/` root (run `docker build -f listener-sqs/Dockerfile .`)
- Images named: `events/listener-sqs`, `events/listener-kafka`, `events/listener-activemq`

## docker-compose.yml (Local Dev)

Single file at `events/docker-compose.yml` with all three brokers:

```yaml
services:
  localstack:
    image: localstack/localstack:4.4
    ports: ["4566:4566"]
    environment:
      SERVICES: sqs
    volumes:
      - ./infra/localstack/init-sqs.sh:/etc/localstack/init/ready.d/init-sqs.sh
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:4566/_localstack/health"]
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 10s

  kafka:
    image: bitnami/kafka:latest
    ports: ["9092:9092"]
    environment:
      KAFKA_CFG_NODE_ID: 0
      KAFKA_CFG_PROCESS_ROLES: broker,controller
      KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: 0@kafka:9093
      KAFKA_CFG_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_CFG_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_CFG_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE: "true"
    healthcheck:
      test: ["CMD", "kafka-topics.sh", "--bootstrap-server", "localhost:9092", "--list"]
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 15s

  artemis:
    image: apache/activemq-artemis:latest
    ports:
      - "61616:61616"   # AMQP/Core
      - "8161:8161"     # Web console
    environment:
      ARTEMIS_USER: artemis
      ARTEMIS_PASSWORD: artemis
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8161/console"]
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 15s
```

No Zookeeper needed — Kafka runs in KRaft mode.

## Publish Scripts

- `scripts/publish-sqs.sh` — sends a message via `awslocal sqs send-message`
- `scripts/publish-kafka.sh` — sends via `kafka-console-producer.sh` or `docker exec`
- `scripts/publish-activemq.sh` — sends via Artemis CLI or curl to the management API

## What Gets Removed

- `events/micronaut-sqs/` directory — replaced by `events/listener-sqs/`
- The `@Scheduled` poll loop in `SqsListenerService` — replaced by `@SqsListener`

## Build & Run

```bash
# Build all modules
mvn clean package

# Build single module
mvn -pl listener-sqs -am package

# Build Docker image
docker build -f listener-sqs/Dockerfile -t events/listener-sqs:latest .

# Local dev — start all brokers
docker compose up -d

# Run a module locally
mvn -pl listener-kafka mn:run
```
