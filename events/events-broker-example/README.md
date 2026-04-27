# Event Listeners — Multi-Module Project

Maven multi-module project with three independent event listener modules built on Micronaut 4.10.4 and Java 21.

| Module | Broker | Listener Pattern |
|---|---|---|
| `listener-sqs` | AWS SQS (LocalStack) | `@Scheduled` poll with `SqsClient` |
| `listener-kafka` | Apache Kafka (KRaft) | `@KafkaListener` + `@Topic` |
| `listener-activemq` | ActiveMQ Artemis | `@JMSListener` + `@Queue` |

## Prerequisites

- Java 21
- Maven 3.8+
- Docker and Docker Compose
- AWS CLI (for SQS publishing)

## Start All Brokers

```bash
docker compose up -d
```

This starts LocalStack (SQS), Kafka (KRaft mode, no Zookeeper), and ActiveMQ Artemis. Wait for all services to become healthy:

```bash
docker compose ps
```

## Build

```bash
# Build all modules
mvn clean package -DskipTests

# Build a single module
mvn -pl listener-sqs -am package -DskipTests
```

## Run

Each module runs independently. Start the one you want to test:

```bash
# SQS listener
mvn -pl listener-sqs mn:run

# Kafka listener
mvn -pl listener-kafka mn:run

# ActiveMQ listener
mvn -pl listener-activemq mn:run
```

## Publish Test Messages

With the brokers running, use the publish scripts to send a test message:

```bash
# SQS (requires AWS CLI)
./scripts/publish-sqs.sh

# Kafka
./scripts/publish-kafka.sh

# ActiveMQ Artemis
./scripts/publish-activemq.sh
```

Each script accepts an optional JSON message as the first argument:

```bash
./scripts/publish-sqs.sh '{"event":"user.signup","userId":"U-999"}'
```

## Docker Images

Each module has its own multi-stage Dockerfile. Build from the `events/` root:

```bash
docker build -f listener-sqs/Dockerfile -t events/listener-sqs:latest .
docker build -f listener-kafka/Dockerfile -t events/listener-kafka:latest .
docker build -f listener-activemq/Dockerfile -t events/listener-activemq:latest .
```

Images use `eclipse-temurin:21-jre-alpine` as the runtime base (~150MB).

## Broker Details

### LocalStack (SQS)

- Gateway: `http://localhost:4566`
- Queue `events-queue` is auto-created on startup via `infra/localstack/init-sqs.sh`
- Credentials: any value works (`test`/`test`)

### Kafka

- Bootstrap: `localhost:9092`
- Runs in KRaft mode (no Zookeeper)
- Topic `events-topic` is auto-created on first produce/consume (`auto.create.topics.enable=true`)

### ActiveMQ Artemis

- Core/AMQP: `localhost:61616`
- Web console: `http://localhost:8161/console`
- Credentials: `artemis`/`artemis`

## Project Structure

```
events/
├── pom.xml                     # Parent POM
├── docker-compose.yml          # All three brokers
├── listener-sqs/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
├── listener-kafka/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
├── listener-activemq/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
├── infra/
│   ├── localstack/init-sqs.sh
│   └── artemis/create-queue.sh
└── scripts/
    ├── publish-sqs.sh
    ├── publish-kafka.sh
    └── publish-activemq.sh
```
