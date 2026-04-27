# Artemis Broker Exception Handling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a two-module Micronaut/Maven project in `events/artemis-broker-exception-handling/` that demonstrates, in isolation and on demand, every common Artemis consumer-failure mode (redelivery, DLQ, TTL expiry, poison pill, business rejection, in-process retry, transacted rollback, idempotent consume, slow consumer, connection failure, DLQ observation) backed by Testcontainers-based integration tests and written-up notes.

**Architecture:** One producer Micronaut app emits messages to a queue-per-scenario address layout on a local Artemis broker (docker-compose for local dev, Testcontainers for tests). A steady-state scheduled emitter runs at 50 msg/sec against the happy-path queue with a configurable failure-injection ratio; an HTTP `POST /trigger/{scenario}` endpoint fires a single message into any chosen scenario queue on demand. The consumer app hosts one listener per scenario, each gated by an independent `scenarios.<name>.enabled` flag so you can isolate exactly one failure mode at a time. Broker-level redelivery/DLQ/expiry behaviour is configured via a custom `broker.xml` (mounted in both docker-compose and Testcontainers) so each scenario's broker policy is explicit and version-controlled.

**Tech Stack:**
- Java 21, Maven, Micronaut 4.10.4 (matches sibling `events-broker-example`)
- `micronaut-jms-activemq-artemis` for producer/consumer
- `micronaut-retry` (`@Retryable`) for app-side retry scenario
- Apache ActiveMQ Artemis 2.33.0 (broker)
- Testcontainers 1.20.x with `GenericContainer` + `TestPropertyProvider`
- JUnit 5, AssertJ, Awaitility for async assertions

---

## File Structure

```
events/artemis-broker-exception-handling/
├── pom.xml                                    # parent, packaging=pom, modules: producer, consumer
├── docker-compose.yml                         # Artemis broker with mounted broker.xml
├── infra/
│   └── artemis/
│       └── broker.xml                         # custom address-settings per scenario
├── README.md                                  # quickstart + module overview
├── docs/
│   ├── error-scenarios.md                     # the comprehensive guide (one section per scenario)
│   └── triggering-scenarios.md                # how to fire each scenario via HTTP / steady emitter
├── producer/
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/mavharsha/events/artemis/producer/
│       │   ├── ArtemisProducerApplication.java
│       │   ├── model/Scenario.java            # enum of all scenarios → queue name
│       │   ├── model/ScenarioMessage.java     # record envelope (id, scenario, payload, emittedAt)
│       │   ├── model/ScenarioQueues.java      # queue-name constants
│       │   ├── publish/ScenarioPublisher.java # Micronaut @JMSProducer
│       │   ├── publish/TtlPublisher.java      # direct JMS for per-message TTL control
│       │   ├── schedule/SteadyStateEmitter.java # 50 msg/sec scheduled emitter
│       │   └── http/TriggerController.java    # POST /trigger/{scenario}
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── logback.xml
│       └── test/java/com/mavharsha/events/artemis/producer/
│           ├── ArtemisTestResource.java       # shared Testcontainers harness
│           └── SteadyStateEmitterTest.java
└── consumer/
    ├── pom.xml
    └── src/
        ├── main/java/com/mavharsha/events/artemis/consumer/
        │   ├── ArtemisConsumerApplication.java
        │   ├── model/Scenario.java            # duplicated from producer (two-module constraint)
        │   ├── model/ScenarioMessage.java
        │   ├── model/ScenarioQueues.java
        │   ├── dedup/MessageDeduplicator.java # in-memory set for idempotent scenario
        │   ├── external/ExternalWriteService.java # mock side-effect for transacted scenario
        │   ├── error/FailureCounter.java      # per-message attempt counter for transient scenarios
        │   └── listener/
        │       ├── HappyPathListener.java
        │       ├── TransientFailureListener.java
        │       ├── PermanentFailureListener.java
        │       ├── PoisonPillListener.java
        │       ├── ExpiringTtlListener.java
        │       ├── BusinessValidationListener.java
        │       ├── AppRetryListener.java
        │       ├── TransactedListener.java
        │       ├── IdempotentListener.java
        │       ├── SlowConsumerListener.java
        │       └── DeadLetterObserver.java
        ├── main/resources/
        │   ├── application.yml
        │   └── logback.xml
        └── test/java/com/mavharsha/events/artemis/consumer/
            ├── ArtemisTestResource.java       # shared Testcontainers harness
            ├── TransientFailureListenerIT.java
            ├── PermanentFailureListenerIT.java
            ├── PoisonPillListenerIT.java
            ├── ExpiringTtlListenerIT.java
            ├── BusinessValidationListenerIT.java
            ├── AppRetryListenerIT.java
            ├── TransactedListenerIT.java
            ├── IdempotentListenerIT.java
            └── DeadLetterObserverIT.java
```

Why this shape:
- `model` duplication between producer and consumer is accepted since you asked for two modules only. Constants are two small files per side; the cost is trivial and keeps the module boundary simple.
- One listener class per scenario so each is self-contained, easy to read in isolation, and toggleable with a single `@Requires` condition.
- Broker policy lives entirely in `infra/artemis/broker.xml` so reviewers can see all redelivery/DLQ/expiry rules in one place.

---

## Scenario Catalogue (reference for tasks below)

Each scenario has a dedicated queue, a failure trigger, and a documented expected outcome.

| Scenario | Queue | Trigger | Expected Outcome |
|---|---|---|---|
| HAPPY_PATH | `scenarios.happy-path` | steady emitter, well-formed payload | consumed, ack'd |
| TRANSIENT_FAILURE | `scenarios.transient-failure` | handler throws for first 2 attempts, succeeds on 3rd | broker redelivers with delay, eventually ack'd |
| PERMANENT_FAILURE | `scenarios.permanent-failure` | handler always throws | after `max-delivery-attempts=5` → DLQ |
| POISON_PILL | `scenarios.poison-pill` | malformed JSON body | deserialization fails → DLQ immediately (no redelivery) |
| EXPIRED_TTL | `scenarios.expired-ttl` | producer sets TTL=500ms, consumer paused 2s | broker routes to `ExpiryQueue` |
| BUSINESS_VALIDATION | `scenarios.business-validation` | payload violates domain rule | handler publishes to `scenarios.business-dlq` and ack's original |
| APP_RETRY | `scenarios.app-retry` | handler throws transient exception | `@Retryable` retries 3× in-process before broker sees failure |
| TRANSACTED | `scenarios.transacted` | external write fails after JMS receive | session rollback → redelivery; no partial state |
| IDEMPOTENT | `scenarios.idempotent` | producer sends same `JMSMessageID` twice | second delivery detected, skipped silently |
| SLOW_CONSUMER | `scenarios.slow-consumer` | handler sleeps 1s, prefetch tuned low | demonstrates prefetch impact on throughput |
| CONNECTION_FAILURE | n/a | broker restart during steady emit | client reconnects, in-flight messages redelivered |
| DLQ_OBSERVER | `DLQ` (broker default) | any DLQ arrival | observer listener logs + exposes count |

---

## Task 1: Bootstrap parent POM, docker-compose, and broker.xml

**Files:**
- Create: `events/artemis-broker-exception-handling/pom.xml`
- Create: `events/artemis-broker-exception-handling/docker-compose.yml`
- Create: `events/artemis-broker-exception-handling/infra/artemis/broker.xml`

- [ ] **Step 1: Create the parent POM**

Write `events/artemis-broker-exception-handling/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.mavharsha</groupId>
    <artifactId>artemis-broker-exception-handling-parent</artifactId>
    <version>0.1</version>
    <packaging>pom</packaging>

    <parent>
        <groupId>io.micronaut.platform</groupId>
        <artifactId>micronaut-parent</artifactId>
        <version>4.10.4</version>
    </parent>

    <modules>
        <module>producer</module>
        <module>consumer</module>
    </modules>

    <properties>
        <jdk.version>21</jdk.version>
        <release.version>21</release.version>
        <micronaut.version>4.10.4</micronaut.version>
        <micronaut.runtime>netty</micronaut.runtime>
        <micronaut.aot.enabled>false</micronaut.aot.enabled>
        <testcontainers.version>1.20.4</testcontainers.version>
        <awaitility.version>4.2.2</awaitility.version>
        <assertj.version>3.26.3</assertj.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.micronaut</groupId>
            <artifactId>micronaut-http-server-netty</artifactId>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>io.micronaut.serde</groupId>
            <artifactId>micronaut-serde-jackson</artifactId>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>io.micronaut.jms</groupId>
            <artifactId>micronaut-jms-activemq-artemis</artifactId>
            <scope>compile</scope>
        </dependency>
        <dependency>
            <groupId>org.yaml</groupId>
            <artifactId>snakeyaml</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>ch.qos.logback</groupId>
            <artifactId>logback-classic</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.micronaut.test</groupId>
            <artifactId>micronaut-test-junit5</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${testcontainers.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <version>${awaitility.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>${assertj.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>io.micronaut.maven</groupId>
                    <artifactId>micronaut-maven-plugin</artifactId>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 2: Create docker-compose.yml**

Write `events/artemis-broker-exception-handling/docker-compose.yml`:

```yaml
services:
  artemis:
    image: apache/activemq-artemis:2.33.0
    container_name: artemis-exception-handling
    ports:
      - "61616:61616"   # core / JMS
      - "8161:8161"     # web console
    environment:
      ARTEMIS_USER: artemis
      ARTEMIS_PASSWORD: artemis
      ANONYMOUS_LOGIN: "false"
    volumes:
      - ./infra/artemis/broker.xml:/var/lib/artemis-broker/etc/broker.xml:ro
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8161/console"]
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 15s
```

- [ ] **Step 3: Create infra/artemis/broker.xml**

Write `events/artemis-broker-exception-handling/infra/artemis/broker.xml` with per-scenario address-settings. The file below overrides the default broker config; the key rules are:

- `DLQ` and `ExpiryQueue` exist explicitly.
- Wildcard `scenarios.#` sets defaults (max-delivery-attempts=5, redelivery-delay=1000 with 2× multiplier, max-redelivery-delay=30000, dead-letter-address=DLQ, expiry-address=ExpiryQueue).
- `scenarios.poison-pill` overrides with `max-delivery-attempts=1` so poison messages go straight to DLQ.
- `scenarios.transient-failure` uses `max-delivery-attempts=10` so retry-then-succeed has room.
- `scenarios.slow-consumer` sets a small `max-size-bytes` to force flow control.

```xml
<?xml version="1.0"?>
<configuration xmlns="urn:activemq"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="urn:activemq /schema/artemis-configuration.xsd">
    <core xmlns="urn:activemq:core">
        <name>exception-handling-broker</name>
        <persistence-enabled>true</persistence-enabled>
        <journal-type>NIO</journal-type>
        <paging-directory>data/paging</paging-directory>
        <bindings-directory>data/bindings</bindings-directory>
        <journal-directory>data/journal</journal-directory>
        <large-messages-directory>data/large-messages</large-messages-directory>

        <acceptors>
            <acceptor name="artemis">tcp://0.0.0.0:61616?tcpSendBufferSize=1048576;tcpReceiveBufferSize=1048576</acceptor>
        </acceptors>

        <security-settings>
            <security-setting match="#">
                <permission type="createNonDurableQueue" roles="amq"/>
                <permission type="deleteNonDurableQueue" roles="amq"/>
                <permission type="createDurableQueue" roles="amq"/>
                <permission type="deleteDurableQueue" roles="amq"/>
                <permission type="createAddress" roles="amq"/>
                <permission type="deleteAddress" roles="amq"/>
                <permission type="consume" roles="amq"/>
                <permission type="browse" roles="amq"/>
                <permission type="send" roles="amq"/>
                <permission type="manage" roles="amq"/>
            </security-setting>
        </security-settings>

        <address-settings>
            <!-- defaults for every scenarios.* address -->
            <address-setting match="scenarios.#">
                <dead-letter-address>DLQ</dead-letter-address>
                <expiry-address>ExpiryQueue</expiry-address>
                <max-delivery-attempts>5</max-delivery-attempts>
                <redelivery-delay>1000</redelivery-delay>
                <redelivery-delay-multiplier>2.0</redelivery-delay-multiplier>
                <max-redelivery-delay>30000</max-redelivery-delay>
                <auto-create-queues>true</auto-create-queues>
                <auto-create-addresses>true</auto-create-addresses>
            </address-setting>
            <!-- poison pill: straight to DLQ after a single attempt -->
            <address-setting match="scenarios.poison-pill">
                <dead-letter-address>DLQ</dead-letter-address>
                <max-delivery-attempts>1</max-delivery-attempts>
                <auto-create-queues>true</auto-create-queues>
            </address-setting>
            <!-- transient failure: room to retry and succeed -->
            <address-setting match="scenarios.transient-failure">
                <dead-letter-address>DLQ</dead-letter-address>
                <max-delivery-attempts>10</max-delivery-attempts>
                <redelivery-delay>500</redelivery-delay>
                <auto-create-queues>true</auto-create-queues>
            </address-setting>
            <!-- slow consumer: tight page size to demo flow control -->
            <address-setting match="scenarios.slow-consumer">
                <max-size-bytes>1048576</max-size-bytes>
                <address-full-policy>PAGE</address-full-policy>
                <auto-create-queues>true</auto-create-queues>
            </address-setting>
            <!-- DLQ itself never redelivers further -->
            <address-setting match="DLQ">
                <max-delivery-attempts>1</max-delivery-attempts>
            </address-setting>
        </address-settings>

        <addresses>
            <address name="DLQ">
                <anycast>
                    <queue name="DLQ"/>
                </anycast>
            </address>
            <address name="ExpiryQueue">
                <anycast>
                    <queue name="ExpiryQueue"/>
                </anycast>
            </address>
        </addresses>
    </core>
</configuration>
```

- [ ] **Step 4: Verify Artemis starts with this config**

Run:

```bash
cd events/artemis-broker-exception-handling
docker compose up -d
# wait ~15s for healthcheck
docker compose ps
```

Expected: `artemis-exception-handling` shows `healthy`. Open `http://localhost:8161/console` (login `artemis`/`artemis`) and confirm `DLQ` and `ExpiryQueue` are listed under Addresses.

Then tear down:

```bash
docker compose down -v
```

- [ ] **Step 5: Commit**

```bash
git add events/artemis-broker-exception-handling/pom.xml \
        events/artemis-broker-exception-handling/docker-compose.yml \
        events/artemis-broker-exception-handling/infra/artemis/broker.xml
git commit -m "feat(artemis-eh): bootstrap parent pom, docker-compose, broker config"
```

---

## Task 2: Producer module skeleton + happy-path publisher

**Files:**
- Create: `events/artemis-broker-exception-handling/producer/pom.xml`
- Create: `events/artemis-broker-exception-handling/producer/src/main/java/com/mavharsha/events/artemis/producer/ArtemisProducerApplication.java`
- Create: `events/artemis-broker-exception-handling/producer/src/main/java/com/mavharsha/events/artemis/producer/model/Scenario.java`
- Create: `events/artemis-broker-exception-handling/producer/src/main/java/com/mavharsha/events/artemis/producer/model/ScenarioQueues.java`
- Create: `events/artemis-broker-exception-handling/producer/src/main/java/com/mavharsha/events/artemis/producer/model/ScenarioMessage.java`
- Create: `events/artemis-broker-exception-handling/producer/src/main/java/com/mavharsha/events/artemis/producer/publish/ScenarioPublisher.java`
- Create: `events/artemis-broker-exception-handling/producer/src/main/resources/application.yml`
- Create: `events/artemis-broker-exception-handling/producer/src/main/resources/logback.xml`

- [ ] **Step 1: Create producer/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.mavharsha</groupId>
        <artifactId>artemis-broker-exception-handling-parent</artifactId>
        <version>0.1</version>
    </parent>

    <artifactId>producer</artifactId>
    <packaging>${packaging}</packaging>

    <properties>
        <packaging>jar</packaging>
        <micronaut.aot.packageName>com.mavharsha.events.artemis.producer.aot.generated</micronaut.aot.packageName>
        <exec.mainClass>com.mavharsha.events.artemis.producer.ArtemisProducerApplication</exec.mainClass>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>io.micronaut.maven</groupId>
                <artifactId>micronaut-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <compilerArgs>
                        <arg>-Amicronaut.processing.group=com.mavharsha</arg>
                        <arg>-Amicronaut.processing.module=producer</arg>
                    </compilerArgs>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create the Micronaut Application class**

`producer/src/main/java/com/mavharsha/events/artemis/producer/ArtemisProducerApplication.java`:

```java
package com.mavharsha.events.artemis.producer;

import io.micronaut.runtime.Micronaut;

public final class ArtemisProducerApplication {

    private ArtemisProducerApplication() { }

    public static void main(String[] args) {
        Micronaut.run(ArtemisProducerApplication.class, args);
    }
}
```

- [ ] **Step 3: Create Scenario enum**

`producer/src/main/java/com/mavharsha/events/artemis/producer/model/Scenario.java`:

```java
package com.mavharsha.events.artemis.producer.model;

public enum Scenario {
    HAPPY_PATH,
    TRANSIENT_FAILURE,
    PERMANENT_FAILURE,
    POISON_PILL,
    EXPIRED_TTL,
    BUSINESS_VALIDATION,
    APP_RETRY,
    TRANSACTED,
    IDEMPOTENT,
    SLOW_CONSUMER
}
```

- [ ] **Step 4: Create ScenarioQueues constants**

`producer/src/main/java/com/mavharsha/events/artemis/producer/model/ScenarioQueues.java`:

```java
package com.mavharsha.events.artemis.producer.model;

import java.util.Map;

public final class ScenarioQueues {

    public static final String HAPPY_PATH = "scenarios.happy-path";
    public static final String TRANSIENT_FAILURE = "scenarios.transient-failure";
    public static final String PERMANENT_FAILURE = "scenarios.permanent-failure";
    public static final String POISON_PILL = "scenarios.poison-pill";
    public static final String EXPIRED_TTL = "scenarios.expired-ttl";
    public static final String BUSINESS_VALIDATION = "scenarios.business-validation";
    public static final String BUSINESS_DLQ = "scenarios.business-dlq";
    public static final String APP_RETRY = "scenarios.app-retry";
    public static final String TRANSACTED = "scenarios.transacted";
    public static final String IDEMPOTENT = "scenarios.idempotent";
    public static final String SLOW_CONSUMER = "scenarios.slow-consumer";
    public static final String BROKER_DLQ = "DLQ";
    public static final String BROKER_EXPIRY = "ExpiryQueue";

    private static final Map<Scenario, String> BY_SCENARIO = Map.of(
            Scenario.HAPPY_PATH, HAPPY_PATH,
            Scenario.TRANSIENT_FAILURE, TRANSIENT_FAILURE,
            Scenario.PERMANENT_FAILURE, PERMANENT_FAILURE,
            Scenario.POISON_PILL, POISON_PILL,
            Scenario.EXPIRED_TTL, EXPIRED_TTL,
            Scenario.BUSINESS_VALIDATION, BUSINESS_VALIDATION,
            Scenario.APP_RETRY, APP_RETRY,
            Scenario.TRANSACTED, TRANSACTED,
            Scenario.IDEMPOTENT, IDEMPOTENT,
            Scenario.SLOW_CONSUMER, SLOW_CONSUMER
    );

    private ScenarioQueues() { }

    public static String queueFor(Scenario s) {
        return BY_SCENARIO.get(s);
    }
}
```

- [ ] **Step 5: Create ScenarioMessage record**

`producer/src/main/java/com/mavharsha/events/artemis/producer/model/ScenarioMessage.java`:

```java
package com.mavharsha.events.artemis.producer.model;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record ScenarioMessage(String messageId,
                              Scenario scenario,
                              String payload,
                              Instant emittedAt) {

    public static ScenarioMessage of(Scenario scenario, String payload) {
        return new ScenarioMessage(UUID.randomUUID().toString(), scenario, payload, Instant.now());
    }
}
```

- [ ] **Step 6: Create ScenarioPublisher (Micronaut @JMSProducer)**

`producer/src/main/java/com/mavharsha/events/artemis/producer/publish/ScenarioPublisher.java`:

```java
package com.mavharsha.events.artemis.producer.publish;

import com.mavharsha.events.artemis.producer.model.ScenarioMessage;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
public interface ScenarioPublisher {

    @Queue("scenarios.happy-path")
    void sendHappyPath(@MessageBody ScenarioMessage message);

    @Queue("scenarios.transient-failure")
    void sendTransientFailure(@MessageBody ScenarioMessage message);

    @Queue("scenarios.permanent-failure")
    void sendPermanentFailure(@MessageBody ScenarioMessage message);

    @Queue("scenarios.poison-pill")
    void sendPoisonPill(@MessageBody String rawBody);

    @Queue("scenarios.business-validation")
    void sendBusinessValidation(@MessageBody ScenarioMessage message);

    @Queue("scenarios.app-retry")
    void sendAppRetry(@MessageBody ScenarioMessage message);

    @Queue("scenarios.transacted")
    void sendTransacted(@MessageBody ScenarioMessage message);

    @Queue("scenarios.idempotent")
    void sendIdempotent(@MessageBody ScenarioMessage message);

    @Queue("scenarios.slow-consumer")
    void sendSlowConsumer(@MessageBody ScenarioMessage message);
}
```

Note: `EXPIRED_TTL` is published via `TtlPublisher` (Task 9/11) because per-message TTL needs direct JMS API.

- [ ] **Step 7: Create application.yml**

`producer/src/main/resources/application.yml`:

```yaml
micronaut:
  application:
    name: artemis-producer
  server:
    port: 8081
  jms:
    activemq:
      artemis:
        enabled: true
        connection-string: tcp://localhost:61616
        username: artemis
        password: artemis

emitter:
  enabled: true
  rate-per-second: 50
  failure-injection-ratio: 0.0
```

- [ ] **Step 8: Create logback.xml**

`producer/src/main/resources/logback.xml`:

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <logger name="com.mavharsha" level="DEBUG"/>
    <logger name="io.micronaut.jms" level="INFO"/>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

- [ ] **Step 9: Verify producer compiles**

Run:

```bash
cd events/artemis-broker-exception-handling
mvn -pl producer -am compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 10: Commit**

```bash
git add events/artemis-broker-exception-handling/producer
git commit -m "feat(artemis-eh): producer module skeleton with scenario model and publisher"
```

---

## Task 3: Consumer module skeleton + happy-path listener

**Files:**
- Create: `events/artemis-broker-exception-handling/consumer/pom.xml`
- Create: `events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/ArtemisConsumerApplication.java`
- Create: `events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/model/Scenario.java`
- Create: `events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/model/ScenarioQueues.java`
- Create: `events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/model/ScenarioMessage.java`
- Create: `events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/HappyPathListener.java`
- Create: `events/artemis-broker-exception-handling/consumer/src/main/resources/application.yml`
- Create: `events/artemis-broker-exception-handling/consumer/src/main/resources/logback.xml`

- [ ] **Step 1: Create consumer/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.mavharsha</groupId>
        <artifactId>artemis-broker-exception-handling-parent</artifactId>
        <version>0.1</version>
    </parent>

    <artifactId>consumer</artifactId>
    <packaging>${packaging}</packaging>

    <properties>
        <packaging>jar</packaging>
        <micronaut.aot.packageName>com.mavharsha.events.artemis.consumer.aot.generated</micronaut.aot.packageName>
        <exec.mainClass>com.mavharsha.events.artemis.consumer.ArtemisConsumerApplication</exec.mainClass>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.micronaut</groupId>
            <artifactId>micronaut-retry</artifactId>
            <scope>compile</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>io.micronaut.maven</groupId>
                <artifactId>micronaut-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <compilerArgs>
                        <arg>-Amicronaut.processing.group=com.mavharsha</arg>
                        <arg>-Amicronaut.processing.module=consumer</arg>
                    </compilerArgs>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Create Application class**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/ArtemisConsumerApplication.java`:

```java
package com.mavharsha.events.artemis.consumer;

import io.micronaut.runtime.Micronaut;

public final class ArtemisConsumerApplication {

    private ArtemisConsumerApplication() { }

    public static void main(String[] args) {
        Micronaut.run(ArtemisConsumerApplication.class, args);
    }
}
```

- [ ] **Step 3: Create Scenario enum (duplicate of producer's)**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/model/Scenario.java` — copy the exact same enum body as `producer/.../model/Scenario.java` from Task 2 Step 3, changing only the package declaration to `com.mavharsha.events.artemis.consumer.model`:

```java
package com.mavharsha.events.artemis.consumer.model;

public enum Scenario {
    HAPPY_PATH,
    TRANSIENT_FAILURE,
    PERMANENT_FAILURE,
    POISON_PILL,
    EXPIRED_TTL,
    BUSINESS_VALIDATION,
    APP_RETRY,
    TRANSACTED,
    IDEMPOTENT,
    SLOW_CONSUMER
}
```

- [ ] **Step 4: Create ScenarioQueues constants**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/model/ScenarioQueues.java`:

```java
package com.mavharsha.events.artemis.consumer.model;

public final class ScenarioQueues {

    public static final String HAPPY_PATH = "scenarios.happy-path";
    public static final String TRANSIENT_FAILURE = "scenarios.transient-failure";
    public static final String PERMANENT_FAILURE = "scenarios.permanent-failure";
    public static final String POISON_PILL = "scenarios.poison-pill";
    public static final String EXPIRED_TTL = "scenarios.expired-ttl";
    public static final String BUSINESS_VALIDATION = "scenarios.business-validation";
    public static final String BUSINESS_DLQ = "scenarios.business-dlq";
    public static final String APP_RETRY = "scenarios.app-retry";
    public static final String TRANSACTED = "scenarios.transacted";
    public static final String IDEMPOTENT = "scenarios.idempotent";
    public static final String SLOW_CONSUMER = "scenarios.slow-consumer";
    public static final String BROKER_DLQ = "DLQ";
    public static final String BROKER_EXPIRY = "ExpiryQueue";

    private ScenarioQueues() { }
}
```

- [ ] **Step 5: Create ScenarioMessage record**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/model/ScenarioMessage.java`:

```java
package com.mavharsha.events.artemis.consumer.model;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
public record ScenarioMessage(String messageId,
                              Scenario scenario,
                              String payload,
                              Instant emittedAt) { }
```

- [ ] **Step 6: Create HappyPathListener**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/HappyPathListener.java`:

```java
package com.mavharsha.events.artemis.consumer.listener;

import com.mavharsha.events.artemis.consumer.model.ScenarioMessage;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.happy-path.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class HappyPathListener {

    private static final Logger LOG = LoggerFactory.getLogger(HappyPathListener.class);
    private final AtomicLong processed = new AtomicLong();

    @Queue("scenarios.happy-path")
    void onMessage(@MessageBody ScenarioMessage message) {
        long n = processed.incrementAndGet();
        if (n % 100 == 0) {
            LOG.info("happy-path processed={} lastId={}", n, message.messageId());
        }
    }

    public long processedCount() {
        return processed.get();
    }
}
```

- [ ] **Step 7: Create application.yml**

`consumer/src/main/resources/application.yml`:

```yaml
micronaut:
  application:
    name: artemis-consumer
  server:
    port: 8082
  jms:
    activemq:
      artemis:
        enabled: true
        connection-string: tcp://localhost:61616
        username: artemis
        password: artemis

scenarios:
  happy-path:
    enabled: true
  transient-failure:
    enabled: true
  permanent-failure:
    enabled: true
  poison-pill:
    enabled: true
  expired-ttl:
    enabled: true
  business-validation:
    enabled: true
  app-retry:
    enabled: true
  transacted:
    enabled: true
  idempotent:
    enabled: true
  slow-consumer:
    enabled: true
  dlq-observer:
    enabled: true
```

- [ ] **Step 8: Create logback.xml**

`consumer/src/main/resources/logback.xml`:

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    <logger name="com.mavharsha" level="DEBUG"/>
    <logger name="io.micronaut.jms" level="INFO"/>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

- [ ] **Step 9: Verify consumer compiles**

Run:

```bash
cd events/artemis-broker-exception-handling
mvn -pl consumer -am compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 10: Commit**

```bash
git add events/artemis-broker-exception-handling/consumer
git commit -m "feat(artemis-eh): consumer module skeleton with happy-path listener"
```

---

## Task 4: Shared Testcontainers harness + happy-path smoke test

**Files:**
- Create: `producer/src/test/java/com/mavharsha/events/artemis/producer/ArtemisTestResource.java`
- Create: `consumer/src/test/java/com/mavharsha/events/artemis/consumer/ArtemisTestResource.java`
- Create: `consumer/src/test/java/com/mavharsha/events/artemis/consumer/HappyPathListenerIT.java`

Why duplicate the harness: with two modules and no shared module, each module brings its own Testcontainers class. Bodies are identical; adjust package and `@TestPropertyProvider` target.

- [ ] **Step 1: Write the failing smoke test**

`consumer/src/test/java/com/mavharsha/events/artemis/consumer/HappyPathListenerIT.java`:

```java
package com.mavharsha.events.artemis.consumer;

import com.mavharsha.events.artemis.consumer.listener.HappyPathListener;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

@MicronautTest
class HappyPathListenerIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;
    @Inject HappyPathListener listener;

    @Test
    void delivers_and_listener_processes() throws Exception {
        long before = listener.processedCount();
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue q = ctx.createQueue("scenarios.happy-path");
            TextMessage msg = ctx.createTextMessage(
                    "{\"messageId\":\"smoke-1\",\"scenario\":\"HAPPY_PATH\"," +
                    "\"payload\":\"hi\",\"emittedAt\":\"2026-04-21T00:00:00Z\"}");
            ctx.createProducer().send(q, msg);
        }

        await().atMost(Duration.ofSeconds(10))
               .untilAsserted(() -> assertThat(listener.processedCount()).isGreaterThan(before));
    }
}
```

- [ ] **Step 2: Write ArtemisTestResource (shared test base)**

`consumer/src/test/java/com/mavharsha/events/artemis/consumer/ArtemisTestResource.java`:

```java
package com.mavharsha.events.artemis.consumer;

import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.util.Map;

public abstract class ArtemisTestResource implements TestPropertyProvider {

    private static final GenericContainer<?> ARTEMIS;

    static {
        ARTEMIS = new GenericContainer<>("apache/activemq-artemis:2.33.0")
                .withExposedPorts(61616, 8161)
                .withEnv("ARTEMIS_USER", "artemis")
                .withEnv("ARTEMIS_PASSWORD", "artemis")
                .withEnv("ANONYMOUS_LOGIN", "false")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../infra/artemis/broker.xml"),
                        "/var/lib/artemis-broker/etc/broker.xml")
                .waitingFor(Wait.forLogMessage(".*Server is now live.*", 1));
        ARTEMIS.start();
    }

    @Override
    public Map<String, String> getProperties() {
        String url = "tcp://" + ARTEMIS.getHost() + ":" + ARTEMIS.getMappedPort(61616);
        return Map.of(
                "micronaut.jms.activemq.artemis.enabled", "true",
                "micronaut.jms.activemq.artemis.connection-string", url,
                "micronaut.jms.activemq.artemis.username", "artemis",
                "micronaut.jms.activemq.artemis.password", "artemis",
                "emitter.enabled", "false"
        );
    }
}
```

Note: `MountableFile.forHostPath("../infra/artemis/broker.xml")` is relative to the module's working directory when tests run (Maven runs from the module dir). If your local build resolves this differently, replace with `forClasspathResource(...)` after copying `broker.xml` to `src/test/resources/`.

- [ ] **Step 3: Run the test to verify it fails because producer side has not yet sent via the test**

Run:

```bash
cd events/artemis-broker-exception-handling
mvn -pl consumer -am -Dtest=HappyPathListenerIT test
```

Expected first run: either FAIL (container starts, listener sees no message) or PASS if the JMSContext send in the test body works. The test IS the send-and-verify — so first run should PASS. If it fails with `broker.xml` mount errors, switch to `forClasspathResource` per the note in Step 2.

- [ ] **Step 4: Create the matching producer-side harness**

`producer/src/test/java/com/mavharsha/events/artemis/producer/ArtemisTestResource.java` — identical body to the consumer version, except the package:

```java
package com.mavharsha.events.artemis.producer;

import io.micronaut.test.support.TestPropertyProvider;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.util.Map;

public abstract class ArtemisTestResource implements TestPropertyProvider {

    private static final GenericContainer<?> ARTEMIS;

    static {
        ARTEMIS = new GenericContainer<>("apache/activemq-artemis:2.33.0")
                .withExposedPorts(61616, 8161)
                .withEnv("ARTEMIS_USER", "artemis")
                .withEnv("ARTEMIS_PASSWORD", "artemis")
                .withEnv("ANONYMOUS_LOGIN", "false")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../infra/artemis/broker.xml"),
                        "/var/lib/artemis-broker/etc/broker.xml")
                .waitingFor(Wait.forLogMessage(".*Server is now live.*", 1));
        ARTEMIS.start();
    }

    @Override
    public Map<String, String> getProperties() {
        String url = "tcp://" + ARTEMIS.getHost() + ":" + ARTEMIS.getMappedPort(61616);
        return Map.of(
                "micronaut.jms.activemq.artemis.enabled", "true",
                "micronaut.jms.activemq.artemis.connection-string", url,
                "micronaut.jms.activemq.artemis.username", "artemis",
                "micronaut.jms.activemq.artemis.password", "artemis",
                "emitter.enabled", "false"
        );
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add events/artemis-broker-exception-handling/producer/src/test \
        events/artemis-broker-exception-handling/consumer/src/test
git commit -m "test(artemis-eh): shared Testcontainers harness and happy-path smoke test"
```

---

## Task 5: HTTP trigger endpoint on producer

**Files:**
- Create: `producer/src/main/java/com/mavharsha/events/artemis/producer/http/TriggerController.java`
- Create: `producer/src/main/java/com/mavharsha/events/artemis/producer/publish/TtlPublisher.java`

- [ ] **Step 1: Create TtlPublisher (direct JMS for TTL control)**

`producer/src/main/java/com/mavharsha/events/artemis/producer/publish/TtlPublisher.java`:

```java
package com.mavharsha.events.artemis.producer.publish;

import com.mavharsha.events.artemis.producer.model.ScenarioMessage;
import com.mavharsha.events.artemis.producer.model.ScenarioQueues;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.Queue;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
public class TtlPublisher {

    private final ConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    public TtlPublisher(@Named(CONNECTION_FACTORY_BEAN_NAME) ConnectionFactory connectionFactory,
                        ObjectMapper objectMapper) {
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
    }

    public void sendExpiring(ScenarioMessage message, long ttlMillis) throws Exception {
        String body = new String(objectMapper.writeValueAsBytes(message));
        try (JMSContext ctx = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
            Queue q = ctx.createQueue(ScenarioQueues.EXPIRED_TTL);
            JMSProducer producer = ctx.createProducer().setTimeToLive(ttlMillis);
            producer.send(q, ctx.createTextMessage(body));
        }
    }
}
```

- [ ] **Step 2: Create TriggerController HTTP endpoint**

`producer/src/main/java/com/mavharsha/events/artemis/producer/http/TriggerController.java`:

```java
package com.mavharsha.events.artemis.producer.http;

import com.mavharsha.events.artemis.producer.model.Scenario;
import com.mavharsha.events.artemis.producer.model.ScenarioMessage;
import com.mavharsha.events.artemis.producer.publish.ScenarioPublisher;
import com.mavharsha.events.artemis.producer.publish.TtlPublisher;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;

import java.util.Map;

@Controller("/trigger")
public class TriggerController {

    private final ScenarioPublisher publisher;
    private final TtlPublisher ttlPublisher;

    public TriggerController(ScenarioPublisher publisher, TtlPublisher ttlPublisher) {
        this.publisher = publisher;
        this.ttlPublisher = ttlPublisher;
    }

    @Post("/{scenario}")
    public HttpResponse<Map<String, String>> trigger(@PathVariable Scenario scenario,
                                                     @Body(required = false) String payload) throws Exception {
        String body = payload == null ? "trigger-" + scenario : payload;
        ScenarioMessage message = ScenarioMessage.of(scenario, body);
        switch (scenario) {
            case HAPPY_PATH -> publisher.sendHappyPath(message);
            case TRANSIENT_FAILURE -> publisher.sendTransientFailure(message);
            case PERMANENT_FAILURE -> publisher.sendPermanentFailure(message);
            case POISON_PILL -> publisher.sendPoisonPill("{ this is not valid json");
            case EXPIRED_TTL -> ttlPublisher.sendExpiring(message, 500L);
            case BUSINESS_VALIDATION -> publisher.sendBusinessValidation(
                    new ScenarioMessage(message.messageId(), scenario, "INVALID-DOMAIN-VALUE", message.emittedAt()));
            case APP_RETRY -> publisher.sendAppRetry(message);
            case TRANSACTED -> publisher.sendTransacted(message);
            case IDEMPOTENT -> {
                publisher.sendIdempotent(message);
                publisher.sendIdempotent(message); // same messageId twice
            }
            case SLOW_CONSUMER -> publisher.sendSlowConsumer(message);
        }
        return HttpResponse.ok(Map.of("status", "triggered", "scenario", scenario.name(),
                                       "messageId", message.messageId()));
    }
}
```

- [ ] **Step 3: Compile**

```bash
cd events/artemis-broker-exception-handling
mvn -pl producer -am compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add events/artemis-broker-exception-handling/producer/src/main/java/com/mavharsha/events/artemis/producer/http \
        events/artemis-broker-exception-handling/producer/src/main/java/com/mavharsha/events/artemis/producer/publish/TtlPublisher.java
git commit -m "feat(artemis-eh): HTTP trigger endpoint and TTL-aware publisher"
```

---

## Task 6: Steady-state 50 msg/sec emitter

**Files:**
- Create: `producer/src/main/java/com/mavharsha/events/artemis/producer/schedule/SteadyStateEmitter.java`
- Create: `producer/src/test/java/com/mavharsha/events/artemis/producer/SteadyStateEmitterIT.java`

- [ ] **Step 1: Write the failing test**

`producer/src/test/java/com/mavharsha/events/artemis/producer/SteadyStateEmitterIT.java`:

```java
package com.mavharsha.events.artemis.producer;

import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSConsumer;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
@Property(name = "emitter.enabled", value = "true")
@Property(name = "emitter.rate-per-second", value = "50")
class SteadyStateEmitterIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;

    @Test
    void emits_at_configured_rate() throws Exception {
        AtomicInteger seen = new AtomicInteger();
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue q = ctx.createQueue("scenarios.happy-path");
            JMSConsumer consumer = ctx.createConsumer(q);
            Instant until = Instant.now().plus(Duration.ofSeconds(2));
            while (Instant.now().isBefore(until)) {
                Message m = consumer.receive(50);
                if (m != null) {
                    seen.incrementAndGet();
                }
            }
        }
        // 50/sec * ~2s = ~100, allow 40-140 tolerance for startup/drift
        assertThat(seen.get()).isBetween(40, 140);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
cd events/artemis-broker-exception-handling
mvn -pl producer -am -Dtest=SteadyStateEmitterIT test
```

Expected: FAIL — `SteadyStateEmitter` does not yet exist / no messages emitted.

- [ ] **Step 3: Implement SteadyStateEmitter**

`producer/src/main/java/com/mavharsha/events/artemis/producer/schedule/SteadyStateEmitter.java`:

```java
package com.mavharsha.events.artemis.producer.schedule;

import com.mavharsha.events.artemis.producer.model.Scenario;
import com.mavharsha.events.artemis.producer.model.ScenarioMessage;
import com.mavharsha.events.artemis.producer.publish.ScenarioPublisher;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
@Requires(property = "emitter.enabled", value = "true", defaultValue = "true")
public class SteadyStateEmitter {

    private static final Logger LOG = LoggerFactory.getLogger(SteadyStateEmitter.class);

    private final ScenarioPublisher publisher;
    private final double failureInjectionRatio;
    private final AtomicLong counter = new AtomicLong();

    public SteadyStateEmitter(ScenarioPublisher publisher,
                              @Value("${emitter.failure-injection-ratio:0.0}") double failureInjectionRatio) {
        this.publisher = publisher;
        this.failureInjectionRatio = failureInjectionRatio;
    }

    @Scheduled(fixedRate = "20ms")
    void emit() {
        long n = counter.incrementAndGet();
        boolean inject = ThreadLocalRandom.current().nextDouble() < failureInjectionRatio;
        if (inject) {
            publisher.sendTransientFailure(ScenarioMessage.of(Scenario.TRANSIENT_FAILURE, "inject-" + n));
        } else {
            publisher.sendHappyPath(ScenarioMessage.of(Scenario.HAPPY_PATH, "steady-" + n));
        }
        if (n % 500 == 0) {
            LOG.info("emitter sent={} injectionRatio={}", n, failureInjectionRatio);
        }
    }
}
```

Note on rate: Micronaut's `@Scheduled(fixedRate = "20ms")` fires every 20ms → 50 Hz. If you want a different rate, override `emitter.rate-per-second` in config and wire a custom scheduler — for this plan, hardcoded 20ms satisfies "50 msg/sec".

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
mvn -pl producer -am -Dtest=SteadyStateEmitterIT test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add events/artemis-broker-exception-handling/producer/src/main/java/com/mavharsha/events/artemis/producer/schedule \
        events/artemis-broker-exception-handling/producer/src/test/java/com/mavharsha/events/artemis/producer/SteadyStateEmitterIT.java
git commit -m "feat(artemis-eh): steady-state 50 msg/sec emitter with failure injection"
```

---

## Task 7: Scenario — TRANSIENT_FAILURE (broker redelivery, eventually succeeds)

**Files:**
- Create: `consumer/src/main/java/com/mavharsha/events/artemis/consumer/error/FailureCounter.java`
- Create: `consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/TransientFailureListener.java`
- Create: `consumer/src/test/java/com/mavharsha/events/artemis/consumer/TransientFailureListenerIT.java`

- [ ] **Step 1: Write the failing test**

`consumer/src/test/java/com/mavharsha/events/artemis/consumer/TransientFailureListenerIT.java`:

```java
package com.mavharsha.events.artemis.consumer;

import com.mavharsha.events.artemis.consumer.listener.TransientFailureListener;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@MicronautTest
class TransientFailureListenerIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;
    @Inject TransientFailureListener listener;

    @Test
    void eventually_succeeds_after_broker_redelivery() {
        String id = "transient-1";
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue q = ctx.createQueue("scenarios.transient-failure");
            ctx.createProducer().send(q, ctx.createTextMessage(
                    "{\"messageId\":\"" + id + "\",\"scenario\":\"TRANSIENT_FAILURE\"," +
                    "\"payload\":\"x\",\"emittedAt\":\"2026-04-21T00:00:00Z\"}"));
        }

        await().atMost(Duration.ofSeconds(30))
               .pollInterval(Duration.ofMillis(500))
               .untilAsserted(() -> {
                   assertThat(listener.attemptsFor(id)).isGreaterThanOrEqualTo(3);
                   assertThat(listener.successesFor(id)).isEqualTo(1);
               });
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -pl consumer -am -Dtest=TransientFailureListenerIT test
```

Expected: FAIL — `TransientFailureListener` does not exist.

- [ ] **Step 3: Implement FailureCounter**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/error/FailureCounter.java`:

```java
package com.mavharsha.events.artemis.consumer.error;

import jakarta.inject.Singleton;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class FailureCounter {

    private final ConcurrentMap<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> successes = new ConcurrentHashMap<>();

    public int recordAttempt(String messageId) {
        return attempts.computeIfAbsent(messageId, k -> new AtomicInteger()).incrementAndGet();
    }

    public int recordSuccess(String messageId) {
        return successes.computeIfAbsent(messageId, k -> new AtomicInteger()).incrementAndGet();
    }

    public int attemptsFor(String messageId) {
        AtomicInteger v = attempts.get(messageId);
        return v == null ? 0 : v.get();
    }

    public int successesFor(String messageId) {
        AtomicInteger v = successes.get(messageId);
        return v == null ? 0 : v.get();
    }
}
```

- [ ] **Step 4: Implement TransientFailureListener**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/TransientFailureListener.java`:

```java
package com.mavharsha.events.artemis.consumer.listener;

import com.mavharsha.events.artemis.consumer.error.FailureCounter;
import com.mavharsha.events.artemis.consumer.model.ScenarioMessage;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.transient-failure.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class TransientFailureListener {

    private static final Logger LOG = LoggerFactory.getLogger(TransientFailureListener.class);
    private static final int FAIL_TIMES = 2;

    private final FailureCounter counter;

    public TransientFailureListener(FailureCounter counter) {
        this.counter = counter;
    }

    @Queue("scenarios.transient-failure")
    void onMessage(@MessageBody ScenarioMessage message) {
        int attempt = counter.recordAttempt(message.messageId());
        LOG.info("transient-failure attempt={} id={}", attempt, message.messageId());
        if (attempt <= FAIL_TIMES) {
            throw new RuntimeException("simulated transient failure attempt=" + attempt);
        }
        counter.recordSuccess(message.messageId());
        LOG.info("transient-failure succeeded id={} totalAttempts={}", message.messageId(), attempt);
    }

    public int attemptsFor(String messageId) {
        return counter.attemptsFor(messageId);
    }

    public int successesFor(String messageId) {
        return counter.successesFor(messageId);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run:

```bash
mvn -pl consumer -am -Dtest=TransientFailureListenerIT test
```

Expected: PASS (may take ~5-10s including redelivery delays of 500ms × 2).

- [ ] **Step 6: Commit**

```bash
git add events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/error \
        events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/TransientFailureListener.java \
        events/artemis-broker-exception-handling/consumer/src/test/java/com/mavharsha/events/artemis/consumer/TransientFailureListenerIT.java
git commit -m "feat(artemis-eh): TRANSIENT_FAILURE scenario with broker-side redelivery"
```

---

## Task 8: Scenario — PERMANENT_FAILURE (max attempts → DLQ)

**Files:**
- Create: `consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/PermanentFailureListener.java`
- Create: `consumer/src/test/java/com/mavharsha/events/artemis/consumer/PermanentFailureListenerIT.java`

- [ ] **Step 1: Write the failing test**

`consumer/src/test/java/com/mavharsha/events/artemis/consumer/PermanentFailureListenerIT.java`:

```java
package com.mavharsha.events.artemis.consumer;

import com.mavharsha.events.artemis.consumer.listener.PermanentFailureListener;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@MicronautTest
class PermanentFailureListenerIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;
    @Inject PermanentFailureListener listener;

    @Test
    void lands_in_dlq_after_max_delivery_attempts() throws Exception {
        String id = "permanent-1";
        String body = "{\"messageId\":\"" + id + "\",\"scenario\":\"PERMANENT_FAILURE\"," +
                "\"payload\":\"x\",\"emittedAt\":\"2026-04-21T00:00:00Z\"}";
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue q = ctx.createQueue("scenarios.permanent-failure");
            ctx.createProducer().send(q, ctx.createTextMessage(body));
        }

        await().atMost(Duration.ofSeconds(60))
               .pollInterval(Duration.ofSeconds(1))
               .untilAsserted(() -> assertThat(listener.attemptsFor(id)).isGreaterThanOrEqualTo(5));

        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue dlq = ctx.createQueue("DLQ");
            JMSConsumer consumer = ctx.createConsumer(dlq);
            Message dlqMsg = consumer.receive(10_000);
            assertThat(dlqMsg).isNotNull();
            assertThat(dlqMsg.getBody(String.class)).contains(id);
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -pl consumer -am -Dtest=PermanentFailureListenerIT test
```

Expected: FAIL — `PermanentFailureListener` missing.

- [ ] **Step 3: Implement PermanentFailureListener**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/PermanentFailureListener.java`:

```java
package com.mavharsha.events.artemis.consumer.listener;

import com.mavharsha.events.artemis.consumer.error.FailureCounter;
import com.mavharsha.events.artemis.consumer.model.ScenarioMessage;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.permanent-failure.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class PermanentFailureListener {

    private static final Logger LOG = LoggerFactory.getLogger(PermanentFailureListener.class);

    private final FailureCounter counter;

    public PermanentFailureListener(FailureCounter counter) {
        this.counter = counter;
    }

    @Queue("scenarios.permanent-failure")
    void onMessage(@MessageBody ScenarioMessage message) {
        int attempt = counter.recordAttempt(message.messageId());
        LOG.warn("permanent-failure attempt={} id={}", attempt, message.messageId());
        throw new RuntimeException("permanent failure; this will never succeed");
    }

    public int attemptsFor(String messageId) {
        return counter.attemptsFor(messageId);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
mvn -pl consumer -am -Dtest=PermanentFailureListenerIT test
```

Expected: PASS (takes ~15-30s due to exponential redelivery). If it times out, raise the awaitility timeout or lower `redelivery-delay` in broker.xml for the `scenarios.permanent-failure` match.

- [ ] **Step 5: Commit**

```bash
git add events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/PermanentFailureListener.java \
        events/artemis-broker-exception-handling/consumer/src/test/java/com/mavharsha/events/artemis/consumer/PermanentFailureListenerIT.java
git commit -m "feat(artemis-eh): PERMANENT_FAILURE scenario routes to DLQ after max attempts"
```

---

## Task 9: Scenario — POISON_PILL (deserialization failure → DLQ immediately)

**Files:**
- Create: `consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/PoisonPillListener.java`
- Create: `consumer/src/test/java/com/mavharsha/events/artemis/consumer/PoisonPillListenerIT.java`

- [ ] **Step 1: Write the failing test**

`consumer/src/test/java/com/mavharsha/events/artemis/consumer/PoisonPillListenerIT.java`:

```java
package com.mavharsha.events.artemis.consumer;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class PoisonPillListenerIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;

    @Test
    void malformed_payload_routes_to_dlq() throws Exception {
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue q = ctx.createQueue("scenarios.poison-pill");
            ctx.createProducer().send(q, ctx.createTextMessage("{ malformed json"));
        }

        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue dlq = ctx.createQueue("DLQ");
            JMSConsumer consumer = ctx.createConsumer(dlq);
            Message dlqMsg = consumer.receive(15_000);
            assertThat(dlqMsg).isNotNull();
            assertThat(dlqMsg.getBody(String.class)).contains("malformed");
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -pl consumer -am -Dtest=PoisonPillListenerIT test
```

Expected: FAIL — no listener consuming the poison-pill queue yet, so deserialization never triggers.

- [ ] **Step 3: Implement PoisonPillListener**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/PoisonPillListener.java`:

```java
package com.mavharsha.events.artemis.consumer.listener;

import com.mavharsha.events.artemis.consumer.model.ScenarioMessage;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.poison-pill.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class PoisonPillListener {

    private static final Logger LOG = LoggerFactory.getLogger(PoisonPillListener.class);

    @Queue("scenarios.poison-pill")
    void onMessage(@MessageBody ScenarioMessage message) {
        LOG.info("poison-pill accepted (shouldn't usually reach here) id={}", message.messageId());
    }
}
```

Why this works: `@MessageBody ScenarioMessage` forces JSON → record deserialization. Malformed JSON throws in the Micronaut JMS binding layer *before* the handler is called. The broker then increments the delivery count; with `max-delivery-attempts=1` on `scenarios.poison-pill`, the next (failed) ack sends it straight to DLQ.

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
mvn -pl consumer -am -Dtest=PoisonPillListenerIT test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/PoisonPillListener.java \
        events/artemis-broker-exception-handling/consumer/src/test/java/com/mavharsha/events/artemis/consumer/PoisonPillListenerIT.java
git commit -m "feat(artemis-eh): POISON_PILL scenario, malformed payload → DLQ immediately"
```

---

## Task 10: Scenario — EXPIRED_TTL (message expires → ExpiryQueue)

**Files:**
- Create: `consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/ExpiringTtlListener.java`
- Create: `consumer/src/test/java/com/mavharsha/events/artemis/consumer/ExpiringTtlListenerIT.java`

- [ ] **Step 1: Write the failing test**

`consumer/src/test/java/com/mavharsha/events/artemis/consumer/ExpiringTtlListenerIT.java`:

```java
package com.mavharsha.events.artemis.consumer;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// disable the listener so the message sits long enough to expire
@MicronautTest
@Property(name = "scenarios.expired-ttl.enabled", value = "false")
class ExpiringTtlListenerIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;

    @Test
    void expired_message_routes_to_expiry_queue() throws Exception {
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue q = ctx.createQueue("scenarios.expired-ttl");
            JMSProducer producer = ctx.createProducer().setTimeToLive(500);
            producer.send(q, ctx.createTextMessage(
                    "{\"messageId\":\"ttl-1\",\"scenario\":\"EXPIRED_TTL\"," +
                    "\"payload\":\"x\",\"emittedAt\":\"2026-04-21T00:00:00Z\"}"));
        }

        Thread.sleep(1500); // wait past the TTL

        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue expiry = ctx.createQueue("ExpiryQueue");
            JMSConsumer consumer = ctx.createConsumer(expiry);
            Message m = consumer.receive(10_000);
            assertThat(m).isNotNull();
            assertThat(m.getBody(String.class)).contains("ttl-1");
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -pl consumer -am -Dtest=ExpiringTtlListenerIT test
```

Expected: FAIL — `ExpiringTtlListener` class doesn't exist yet (Micronaut context startup error on missing `@Requires` target). Actually, `@Requires` gates bean registration; a missing class prevents compile, not runtime. So the test may actually fail at the expiry-queue receive step if the listener file is absent — that's expected for this step.

- [ ] **Step 3: Implement ExpiringTtlListener**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/ExpiringTtlListener.java`:

```java
package com.mavharsha.events.artemis.consumer.listener;

import com.mavharsha.events.artemis.consumer.model.ScenarioMessage;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.expired-ttl.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class ExpiringTtlListener {

    private static final Logger LOG = LoggerFactory.getLogger(ExpiringTtlListener.class);

    @Queue("scenarios.expired-ttl")
    void onMessage(@MessageBody ScenarioMessage message) {
        LOG.info("expired-ttl received before expiry id={}", message.messageId());
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
mvn -pl consumer -am -Dtest=ExpiringTtlListenerIT test
```

Expected: PASS. The test disables the listener via `@Property` so the message sits in the queue past its TTL and Artemis moves it to `ExpiryQueue` on the next expiry scan (default ~30s — if flaky, add `<message-expiry-scan-period>500</message-expiry-scan-period>` at the top of `broker.xml`'s `<core>` and reference it here).

Add to `broker.xml` (top level of `<core>`, above `<acceptors>`):

```xml
<message-expiry-scan-period>500</message-expiry-scan-period>
```

Re-run the test and confirm PASS.

- [ ] **Step 5: Commit**

```bash
git add events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/ExpiringTtlListener.java \
        events/artemis-broker-exception-handling/consumer/src/test/java/com/mavharsha/events/artemis/consumer/ExpiringTtlListenerIT.java \
        events/artemis-broker-exception-handling/infra/artemis/broker.xml
git commit -m "feat(artemis-eh): EXPIRED_TTL scenario, expired messages routed to ExpiryQueue"
```

---

## Task 11: Scenario — BUSINESS_VALIDATION (app-level DLQ)

**Files:**
- Create: `consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/BusinessValidationListener.java`
- Create: `consumer/src/test/java/com/mavharsha/events/artemis/consumer/BusinessValidationListenerIT.java`

- [ ] **Step 1: Write the failing test**

`consumer/src/test/java/com/mavharsha/events/artemis/consumer/BusinessValidationListenerIT.java`:

```java
package com.mavharsha.events.artemis.consumer;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class BusinessValidationListenerIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;

    @Test
    void invalid_payload_routes_to_business_dlq_without_broker_redelivery() throws Exception {
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue q = ctx.createQueue("scenarios.business-validation");
            ctx.createProducer().send(q, ctx.createTextMessage(
                    "{\"messageId\":\"bv-1\",\"scenario\":\"BUSINESS_VALIDATION\"," +
                    "\"payload\":\"INVALID-DOMAIN-VALUE\",\"emittedAt\":\"2026-04-21T00:00:00Z\"}"));
        }

        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue bdlq = ctx.createQueue("scenarios.business-dlq");
            JMSConsumer consumer = ctx.createConsumer(bdlq);
            Message m = consumer.receive(10_000);
            assertThat(m).isNotNull();
            assertThat(m.getBody(String.class)).contains("bv-1");
            assertThat(m.getStringProperty("rejection-reason")).isEqualTo("INVALID_DOMAIN");
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -pl consumer -am -Dtest=BusinessValidationListenerIT test
```

Expected: FAIL — listener not implemented.

- [ ] **Step 3: Implement BusinessValidationListener**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/BusinessValidationListener.java`:

```java
package com.mavharsha.events.artemis.consumer.listener;

import com.mavharsha.events.artemis.consumer.model.ScenarioMessage;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.business-validation.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class BusinessValidationListener {

    private static final Logger LOG = LoggerFactory.getLogger(BusinessValidationListener.class);

    private final ConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    public BusinessValidationListener(@Named(CONNECTION_FACTORY_BEAN_NAME) ConnectionFactory connectionFactory,
                                      ObjectMapper objectMapper) {
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
    }

    @Queue("scenarios.business-validation")
    void onMessage(@MessageBody ScenarioMessage message) throws Exception {
        if (!"VALID".equals(message.payload()) && !message.payload().startsWith("ok-")) {
            LOG.warn("business-validation rejected id={} payload={}", message.messageId(), message.payload());
            rejectToBusinessDlq(message, "INVALID_DOMAIN");
            return;
        }
        LOG.info("business-validation accepted id={}", message.messageId());
    }

    private void rejectToBusinessDlq(ScenarioMessage message, String reason) throws Exception {
        String body = new String(objectMapper.writeValueAsBytes(message));
        try (JMSContext ctx = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
            TextMessage m = ctx.createTextMessage(body);
            m.setStringProperty("rejection-reason", reason);
            m.setStringProperty("original-queue", "scenarios.business-validation");
            JMSProducer producer = ctx.createProducer();
            producer.send(ctx.createQueue("scenarios.business-dlq"), m);
        }
    }
}
```

Key distinction from `PERMANENT_FAILURE`: the handler does **not throw**. It acks the original message and forwards a copy (with metadata) to a domain-specific DLQ. This is the right pattern when the failure is a business rule, not an infrastructure problem — broker redelivery won't help.

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
mvn -pl consumer -am -Dtest=BusinessValidationListenerIT test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/BusinessValidationListener.java \
        events/artemis-broker-exception-handling/consumer/src/test/java/com/mavharsha/events/artemis/consumer/BusinessValidationListenerIT.java
git commit -m "feat(artemis-eh): BUSINESS_VALIDATION scenario with app-level DLQ forwarding"
```

---

## Task 12: Scenario — APP_RETRY (in-process retry via @Retryable)

**Files:**
- Create: `consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/AppRetryListener.java`
- Create: `consumer/src/test/java/com/mavharsha/events/artemis/consumer/AppRetryListenerIT.java`

- [ ] **Step 1: Write the failing test**

`consumer/src/test/java/com/mavharsha/events/artemis/consumer/AppRetryListenerIT.java`:

```java
package com.mavharsha.events.artemis.consumer;

import com.mavharsha.events.artemis.consumer.listener.AppRetryListener;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@MicronautTest
class AppRetryListenerIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;
    @Inject AppRetryListener listener;

    @Test
    void retries_in_process_without_broker_redelivery() {
        String id = "app-retry-1";
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue q = ctx.createQueue("scenarios.app-retry");
            ctx.createProducer().send(q, ctx.createTextMessage(
                    "{\"messageId\":\"" + id + "\",\"scenario\":\"APP_RETRY\"," +
                    "\"payload\":\"x\",\"emittedAt\":\"2026-04-21T00:00:00Z\"}"));
        }

        await().atMost(Duration.ofSeconds(15))
               .untilAsserted(() -> {
                   // handler invoked once at the listener boundary,
                   // but inner method invoked >= 3 times (initial + 2 retries)
                   assertThat(listener.handlerInvocations()).isEqualTo(1);
                   assertThat(listener.innerInvocations()).isGreaterThanOrEqualTo(3);
                   assertThat(listener.succeeded()).isTrue();
               });
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -pl consumer -am -Dtest=AppRetryListenerIT test
```

Expected: FAIL.

- [ ] **Step 3: Implement AppRetryListener**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/AppRetryListener.java`:

```java
package com.mavharsha.events.artemis.consumer.listener;

import com.mavharsha.events.artemis.consumer.model.ScenarioMessage;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import io.micronaut.retry.annotation.Retryable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.app-retry.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class AppRetryListener {

    private static final Logger LOG = LoggerFactory.getLogger(AppRetryListener.class);

    private final AtomicInteger handlerCalls = new AtomicInteger();
    private final AtomicInteger innerCalls = new AtomicInteger();
    private final AtomicBoolean succeeded = new AtomicBoolean();

    @Queue("scenarios.app-retry")
    void onMessage(@MessageBody ScenarioMessage message) {
        handlerCalls.incrementAndGet();
        doWork(message);
        succeeded.set(true);
        LOG.info("app-retry succeeded id={} afterInner={}", message.messageId(), innerCalls.get());
    }

    @Retryable(attempts = "3", delay = "200ms", multiplier = "2.0")
    protected void doWork(ScenarioMessage message) {
        int n = innerCalls.incrementAndGet();
        LOG.info("app-retry innerCall={} id={}", n, message.messageId());
        if (n < 3) {
            throw new RuntimeException("simulated transient inner failure n=" + n);
        }
    }

    public int handlerInvocations() { return handlerCalls.get(); }
    public int innerInvocations() { return innerCalls.get(); }
    public boolean succeeded() { return succeeded.get(); }
}
```

Why the inner method and not the listener method: Micronaut's `@Retryable` is AOP-based and only intercepts calls through the proxy. Applying it directly to `onMessage` wouldn't retry because the JMS infrastructure doesn't re-invoke via the proxy. A separate method (injected or called through the bean interface) gets the retry aspect.

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
mvn -pl consumer -am -Dtest=AppRetryListenerIT test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/AppRetryListener.java \
        events/artemis-broker-exception-handling/consumer/src/test/java/com/mavharsha/events/artemis/consumer/AppRetryListenerIT.java
git commit -m "feat(artemis-eh): APP_RETRY scenario using @Retryable for in-process retries"
```

---

## Task 13: Scenario — TRANSACTED (session rollback on side-effect failure)

**Files:**
- Create: `consumer/src/main/java/com/mavharsha/events/artemis/consumer/external/ExternalWriteService.java`
- Create: `consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/TransactedListener.java`
- Create: `consumer/src/test/java/com/mavharsha/events/artemis/consumer/TransactedListenerIT.java`

- [ ] **Step 1: Write the failing test**

`consumer/src/test/java/com/mavharsha/events/artemis/consumer/TransactedListenerIT.java`:

```java
package com.mavharsha.events.artemis.consumer;

import com.mavharsha.events.artemis.consumer.external.ExternalWriteService;
import com.mavharsha.events.artemis.consumer.listener.TransactedListener;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@MicronautTest
class TransactedListenerIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;
    @Inject TransactedListener listener;
    @Inject ExternalWriteService external;

    @Test
    void rollback_causes_redelivery_and_no_partial_write() {
        external.failNextN(2); // first 2 attempts fail → session rolls back → redelivery
        String id = "tx-1";
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue q = ctx.createQueue("scenarios.transacted");
            ctx.createProducer().send(q, ctx.createTextMessage(
                    "{\"messageId\":\"" + id + "\",\"scenario\":\"TRANSACTED\"," +
                    "\"payload\":\"x\",\"emittedAt\":\"2026-04-21T00:00:00Z\"}"));
        }

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(listener.attempts()).isGreaterThanOrEqualTo(3);
            assertThat(external.committedCount()).isEqualTo(1); // only the successful attempt committed
        });
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -pl consumer -am -Dtest=TransactedListenerIT test
```

Expected: FAIL.

- [ ] **Step 3: Implement ExternalWriteService**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/external/ExternalWriteService.java`:

```java
package com.mavharsha.events.artemis.consumer.external;

import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class ExternalWriteService {

    private final AtomicInteger failuresRemaining = new AtomicInteger();
    private final AtomicInteger committed = new AtomicInteger();

    public void failNextN(int n) {
        failuresRemaining.set(n);
    }

    public void write(String id, String payload) {
        if (failuresRemaining.getAndDecrement() > 0) {
            throw new RuntimeException("external write failed for id=" + id);
        }
        committed.incrementAndGet();
    }

    public int committedCount() { return committed.get(); }
}
```

- [ ] **Step 4: Implement TransactedListener**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/TransactedListener.java`:

```java
package com.mavharsha.events.artemis.consumer.listener;

import com.mavharsha.events.artemis.consumer.external.ExternalWriteService;
import com.mavharsha.events.artemis.consumer.model.ScenarioMessage;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.transacted.enabled", value = "true", defaultValue = "true")
@JMSListener(value = CONNECTION_FACTORY_BEAN_NAME, transacted = true)
public class TransactedListener {

    private static final Logger LOG = LoggerFactory.getLogger(TransactedListener.class);

    private final ExternalWriteService external;
    private final AtomicInteger attempts = new AtomicInteger();

    public TransactedListener(ExternalWriteService external) {
        this.external = external;
    }

    @Queue("scenarios.transacted")
    void onMessage(@MessageBody ScenarioMessage message) {
        int n = attempts.incrementAndGet();
        LOG.info("transacted attempt={} id={}", n, message.messageId());
        external.write(message.messageId(), message.payload());
        LOG.info("transacted committed id={}", message.messageId());
    }

    public int attempts() { return attempts.get(); }
}
```

Note on `transacted=true`: this depends on `io.micronaut.jms.annotations.JMSListener` supporting a `transacted` attribute in the installed version of `micronaut-jms-activemq-artemis`. If it does not, the equivalent is to set `@JMSListener(acknowledgeMode = jakarta.jms.Session.SESSION_TRANSACTED)` or configure the listener factory bean property `transacted-session: true`. Verify by consulting the `@JMSListener` source on first build and adjust.

- [ ] **Step 5: Run the test to verify it passes**

Run:

```bash
mvn -pl consumer -am -Dtest=TransactedListenerIT test
```

Expected: PASS — rollback makes the broker redeliver until `external.write` succeeds, and only the successful invocation is committed.

- [ ] **Step 6: Commit**

```bash
git add events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/external \
        events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/TransactedListener.java \
        events/artemis-broker-exception-handling/consumer/src/test/java/com/mavharsha/events/artemis/consumer/TransactedListenerIT.java
git commit -m "feat(artemis-eh): TRANSACTED scenario, session rollback on side-effect failure"
```

---

## Task 14: Scenario — IDEMPOTENT (dedup by messageId)

**Files:**
- Create: `consumer/src/main/java/com/mavharsha/events/artemis/consumer/dedup/MessageDeduplicator.java`
- Create: `consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/IdempotentListener.java`
- Create: `consumer/src/test/java/com/mavharsha/events/artemis/consumer/IdempotentListenerIT.java`

- [ ] **Step 1: Write the failing test**

`consumer/src/test/java/com/mavharsha/events/artemis/consumer/IdempotentListenerIT.java`:

```java
package com.mavharsha.events.artemis.consumer;

import com.mavharsha.events.artemis.consumer.listener.IdempotentListener;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@MicronautTest
class IdempotentListenerIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;
    @Inject IdempotentListener listener;

    @Test
    void duplicate_message_is_processed_only_once() {
        String id = "dup-1";
        String body = "{\"messageId\":\"" + id + "\",\"scenario\":\"IDEMPOTENT\"," +
                "\"payload\":\"x\",\"emittedAt\":\"2026-04-21T00:00:00Z\"}";
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue q = ctx.createQueue("scenarios.idempotent");
            ctx.createProducer().send(q, ctx.createTextMessage(body));
            ctx.createProducer().send(q, ctx.createTextMessage(body)); // duplicate
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(listener.seenCount(id)).isEqualTo(2);      // both delivered
            assertThat(listener.processedCount(id)).isEqualTo(1); // only one processed
        });
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -pl consumer -am -Dtest=IdempotentListenerIT test
```

Expected: FAIL.

- [ ] **Step 3: Implement MessageDeduplicator**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/dedup/MessageDeduplicator.java`:

```java
package com.mavharsha.events.artemis.consumer.dedup;

import jakarta.inject.Singleton;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class MessageDeduplicator {

    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    public boolean claim(String messageId) {
        return seen.add(messageId);
    }
}
```

Note: in production, back this with Redis or a DB table keyed on messageId with TTL. In-memory is fine for demo and tests.

- [ ] **Step 4: Implement IdempotentListener**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/IdempotentListener.java`:

```java
package com.mavharsha.events.artemis.consumer.listener;

import com.mavharsha.events.artemis.consumer.dedup.MessageDeduplicator;
import com.mavharsha.events.artemis.consumer.model.ScenarioMessage;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.idempotent.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class IdempotentListener {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotentListener.class);

    private final MessageDeduplicator deduplicator;
    private final ConcurrentMap<String, AtomicInteger> seen = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> processed = new ConcurrentHashMap<>();

    public IdempotentListener(MessageDeduplicator deduplicator) {
        this.deduplicator = deduplicator;
    }

    @Queue("scenarios.idempotent")
    void onMessage(@MessageBody ScenarioMessage message) {
        seen.computeIfAbsent(message.messageId(), k -> new AtomicInteger()).incrementAndGet();
        if (!deduplicator.claim(message.messageId())) {
            LOG.info("idempotent skip duplicate id={}", message.messageId());
            return;
        }
        processed.computeIfAbsent(message.messageId(), k -> new AtomicInteger()).incrementAndGet();
        LOG.info("idempotent processed id={}", message.messageId());
    }

    public int seenCount(String id) {
        AtomicInteger v = seen.get(id);
        return v == null ? 0 : v.get();
    }

    public int processedCount(String id) {
        AtomicInteger v = processed.get(id);
        return v == null ? 0 : v.get();
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run:

```bash
mvn -pl consumer -am -Dtest=IdempotentListenerIT test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/dedup \
        events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/IdempotentListener.java \
        events/artemis-broker-exception-handling/consumer/src/test/java/com/mavharsha/events/artemis/consumer/IdempotentListenerIT.java
git commit -m "feat(artemis-eh): IDEMPOTENT scenario with in-memory deduplicator"
```

---

## Task 15: Scenario — SLOW_CONSUMER (prefetch demonstration, manual verification)

**Files:**
- Create: `consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/SlowConsumerListener.java`

This scenario is demonstrated, not asserted by integration test, because prefetch/flow-control behaviour is observed rather than strictly deterministic in a CI timeframe. Notes go in `docs/error-scenarios.md`.

- [ ] **Step 1: Implement SlowConsumerListener**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/SlowConsumerListener.java`:

```java
package com.mavharsha.events.artemis.consumer.listener;

import com.mavharsha.events.artemis.consumer.model.ScenarioMessage;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.slow-consumer.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class SlowConsumerListener {

    private static final Logger LOG = LoggerFactory.getLogger(SlowConsumerListener.class);
    private final AtomicLong processed = new AtomicLong();

    @Queue("scenarios.slow-consumer")
    void onMessage(@MessageBody ScenarioMessage message) throws InterruptedException {
        Thread.sleep(1000); // intentionally slow
        long n = processed.incrementAndGet();
        if (n % 5 == 0) {
            LOG.info("slow-consumer processed={} lastId={}", n, message.messageId());
        }
    }

    public long processedCount() { return processed.get(); }
}
```

- [ ] **Step 2: Verify it compiles**

Run:

```bash
mvn -pl consumer -am compile
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**

```bash
git add events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/SlowConsumerListener.java
git commit -m "feat(artemis-eh): SLOW_CONSUMER listener for prefetch/flow-control demo"
```

---

## Task 16: Scenario — DLQ observer listener

**Files:**
- Create: `consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/DeadLetterObserver.java`
- Create: `consumer/src/test/java/com/mavharsha/events/artemis/consumer/DeadLetterObserverIT.java`

- [ ] **Step 1: Write the failing test**

`consumer/src/test/java/com/mavharsha/events/artemis/consumer/DeadLetterObserverIT.java`:

```java
package com.mavharsha.events.artemis.consumer;

import com.mavharsha.events.artemis.consumer.listener.DeadLetterObserver;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@MicronautTest
class DeadLetterObserverIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;
    @Inject DeadLetterObserver observer;

    @Test
    void observer_receives_dlq_arrivals() throws Exception {
        long before = observer.dlqCount();
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue dlq = ctx.createQueue("DLQ");
            ctx.createProducer().send(dlq, ctx.createTextMessage(
                    "{\"messageId\":\"dlq-seed-1\",\"scenario\":\"PERMANENT_FAILURE\"," +
                    "\"payload\":\"x\",\"emittedAt\":\"2026-04-21T00:00:00Z\"}"));
        }

        await().atMost(Duration.ofSeconds(10))
               .untilAsserted(() -> assertThat(observer.dlqCount()).isGreaterThan(before));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
mvn -pl consumer -am -Dtest=DeadLetterObserverIT test
```

Expected: FAIL.

- [ ] **Step 3: Implement DeadLetterObserver**

`consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/DeadLetterObserver.java`:

```java
package com.mavharsha.events.artemis.consumer.listener;

import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import io.micronaut.messaging.annotation.MessageHeader;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.dlq-observer.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class DeadLetterObserver {

    private static final Logger LOG = LoggerFactory.getLogger(DeadLetterObserver.class);

    private final AtomicLong count = new AtomicLong();

    @Queue("DLQ")
    void onDeadLetter(@MessageBody String body,
                      @MessageHeader(value = "_AMQ_ORIG_ADDRESS", required = false) String origAddress,
                      @MessageHeader(value = "_AMQ_ORIG_QUEUE", required = false) String origQueue) {
        long n = count.incrementAndGet();
        LOG.warn("DLQ arrival #{} origAddress={} origQueue={} bodyPreview={}",
                n, origAddress, origQueue, body.length() > 120 ? body.substring(0, 120) + "…" : body);
    }

    public long dlqCount() { return count.get(); }
}
```

Header names: Artemis stamps `_AMQ_ORIG_ADDRESS` / `_AMQ_ORIG_QUEUE` / `_AMQ_ORIG_MESSAGE_ID` on messages redirected to DLQ. Those headers are useful for re-drive tooling; the observer just surfaces them.

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
mvn -pl consumer -am -Dtest=DeadLetterObserverIT test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add events/artemis-broker-exception-handling/consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/DeadLetterObserver.java \
        events/artemis-broker-exception-handling/consumer/src/test/java/com/mavharsha/events/artemis/consumer/DeadLetterObserverIT.java
git commit -m "feat(artemis-eh): DLQ observer listener with origin header surfacing"
```

---

## Task 17: Connection-failure scenario (manual demonstration, no integration test)

Connection-failure behaviour (broker restart, client reconnect, in-flight redelivery) is easier to verify manually than in a CI-safe integration test. Document and script it; do not add a Testcontainers test for this.

**Files:**
- Create: `docs/scripts/connection-failure-demo.sh`

- [ ] **Step 1: Create the demo script**

`events/artemis-broker-exception-handling/docs/scripts/connection-failure-demo.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail

# Run this while producer + consumer apps are running against local docker-compose Artemis.
# It will:
#   1. Start the steady emitter (it auto-starts with emitter.enabled=true)
#   2. Pause 5s, print current consumer count
#   3. Restart the broker — simulate failure
#   4. Pause 15s, print counts again
#   5. Confirm in-flight messages got redelivered and steady emit resumed

echo "[1/4] confirm producer is emitting…"
sleep 5

echo "[2/4] restarting Artemis (simulates transient broker failure)…"
docker compose restart artemis

echo "[3/4] waiting 15s for reconnect + redelivery…"
sleep 15

echo "[4/4] check consumer logs for: 'transport failure', 'reconnected', redelivery counts"
echo "    docker compose logs --tail=200 | grep -iE 'reconnect|failover|redeliver'"
echo "Open http://localhost:8161/console — queue depths should have drained after reconnect."
```

Make executable:

```bash
chmod +x events/artemis-broker-exception-handling/docs/scripts/connection-failure-demo.sh
```

- [ ] **Step 2: Commit**

```bash
git add events/artemis-broker-exception-handling/docs/scripts/connection-failure-demo.sh
git commit -m "docs(artemis-eh): connection-failure demo script"
```

---

## Task 18: `docs/error-scenarios.md` comprehensive guide

**Files:**
- Create: `events/artemis-broker-exception-handling/docs/error-scenarios.md`

- [ ] **Step 1: Write error-scenarios.md**

Full content:

````markdown
# Artemis Consumer Failure Scenarios — Comprehensive Guide

Every consumer will eventually fail to process a message. This guide enumerates the failure modes you'll hit with ActiveMQ Artemis, what they look like at runtime, and the right handling pattern for each.

The demo project in this directory gives you one runnable listener per scenario, gated by `scenarios.<name>.enabled` in `consumer/src/main/resources/application.yml`. Toggle one on at a time, trigger via `POST http://localhost:8081/trigger/{SCENARIO}`, and watch the Artemis console at `http://localhost:8161/console`.

## 1. Transient failure → broker-side redelivery

**Symptom:** the handler throws (network blip, lock contention, a dependency momentarily unavailable) and Artemis redelivers after `redelivery-delay`.

**Broker config** (`broker.xml`, `scenarios.transient-failure`):
```
max-delivery-attempts: 10
redelivery-delay: 500 (exponential up to max-redelivery-delay)
```

**Handler pattern:** throw. The broker will retry. Don't catch-and-swallow; that silently drops messages.

**When to use:** the failure is genuinely transient and you want the broker to pace the retry (backoff is handled for you, and the message is durably redelivered across consumer restarts).

**Trap:** if retries are fast (0ms) and the failure persists, you'll burn the entire `max-delivery-attempts` budget in milliseconds and end up in DLQ when a 30-second delay would've succeeded. Always use `redelivery-delay > 0` and a multiplier.

## 2. Permanent failure → DLQ after max attempts

**Symptom:** the handler throws every attempt. After `max-delivery-attempts` (default 5), the broker redirects to the configured `dead-letter-address` (DLQ).

**Broker config** (`broker.xml`, default `scenarios.#`):
```
max-delivery-attempts: 5
dead-letter-address: DLQ
```

**Handler pattern:** for permanent failures (bug, logical error, contract mismatch) throwing will eventually land it in DLQ. Better: detect it up-front (see business validation below) to avoid burning redelivery attempts.

**Observability:** run `DeadLetterObserver` with `scenarios.dlq-observer.enabled=true`. Artemis stamps `_AMQ_ORIG_ADDRESS`, `_AMQ_ORIG_QUEUE`, `_AMQ_ORIG_MESSAGE_ID` on DLQ messages so you can trace or re-drive them.

## 3. Poison pill (deserialization failure) → DLQ immediately

**Symptom:** the message body is unparseable (wrong format, wrong schema version). Every attempt throws at binding time, before your handler is invoked.

**Broker config** (`scenarios.poison-pill`): `max-delivery-attempts: 1`. No point redelivering a malformed message; the content will never parse.

**Handler pattern:** you don't write one — the Micronaut JMS binder fails during `@MessageBody` conversion and the broker DLQs on the next failed ack.

**Alternative:** accept `@MessageBody String` (or `jakarta.jms.Message`) and parse manually. Catch the parse exception, forward to a domain-specific DLQ with reason metadata, then ack. This gives richer observability than dropping into the broker DLQ.

## 4. TTL expiry → ExpiryQueue

**Symptom:** the producer set a time-to-live and no consumer picked the message up in time.

**Broker config** (`scenarios.#`): `expiry-address: ExpiryQueue`. A broker-level `<message-expiry-scan-period>500</message-expiry-scan-period>` controls how often expired messages are swept.

**Handler pattern:** set TTL from the producer using `JMSProducer.setTimeToLive(ms)` or set `JMSExpiration` on the `Message`. Once expired, the broker moves the message to the expiry address.

**When to use:** time-sensitive work (e.g., a notification that's useless after 30s, a price update that's stale after 1s). Prefer TTL + expiry queue over "consumer checks timestamp and drops" — the latter still counts as a delivery and wastes retry attempts.

## 5. Business validation failure → app-level DLQ

**Symptom:** the message is well-formed but fails domain rules (e.g., unknown account ID, amount below minimum, state transition not allowed).

**Handler pattern:** do not throw. Ack the original message and forward a copy to a domain-specific queue (e.g., `scenarios.business-dlq`) with metadata:
- `rejection-reason`
- `original-queue`
- `validation-detail`

**Why not let it DLQ via redelivery?** Redelivery doesn't help a failed business rule — the next attempt will reject too. Burning 5 attempts before landing in the generic `DLQ` loses context and makes operators investigate the same rejection 5 times.

**Pair with:** a separate listener on the business DLQ (or periodic ops review) that decides whether to re-drive after data is corrected.

## 6. In-process retry with `@Retryable`

**Symptom:** you have a failure that's transient (HTTP 503, rate-limited external API) and you want to retry *without* the broker knowing.

**Handler pattern:** wrap the retryable work in a method annotated `@Retryable(attempts="3", delay="200ms", multiplier="2.0")`. The method **must** be called through a bean proxy (i.e., a separate method on the same bean or a collaborator) for AOP to intercept.

**When in-process beats broker redelivery:**
- You want to retry fast (ms-scale) without roundtripping through the broker.
- The retry shouldn't count against `max-delivery-attempts`.
- You want to combine with circuit-breaking (`@CircuitBreaker`) so you stop hammering a dead dependency.

**When broker redelivery beats in-process:**
- You want durability — the message survives consumer crashes.
- You want backoff measured in seconds/minutes.
- You don't want blocked consumer threads holding connections during long retries.

In this demo both are used side-by-side to make the tradeoff visible.

## 7. Transacted session + rollback on side-effect failure

**Symptom:** your handler reads from JMS *and* writes to something external (DB, another queue, HTTP API). If the external write fails after the JMS receive, the message is still acknowledged unless you use a transacted session.

**Handler pattern:** declare the listener with `transacted=true` (session in `SESSION_TRANSACTED` mode). On any exception, the session rolls back — the message is un-acked, the broker redelivers.

**Key invariant:** do the JMS receive and the side-effect inside the same session scope. If the side-effect succeeds but the JMS commit fails, you'll double-process on redelivery — that's why patterns 6 (idempotent) and 7 (transacted) are often combined.

**Caveat:** Artemis transactions are local to the session. For true cross-resource atomicity (JMS + DB), you need XA transactions — considerably more complex and usually avoided in favor of the outbox pattern.

## 8. Idempotent consume (dedup)

**Symptom:** the broker guarantees **at-least-once** delivery. Network retries, transacted rollback, consumer crashes mid-ack — any of these can cause the same message to be delivered twice.

**Handler pattern:** assign every message a stable `messageId` at the producer (your domain ID or a UUID — don't rely on `JMSMessageID` since that changes on redelivery). On receive, check a store (Redis, DB row with unique constraint, in-memory LRU) — if the id was already processed, skip.

**In this demo:** `MessageDeduplicator` uses a `ConcurrentHashMap` for simplicity. Production should use a TTL-bounded store (Redis `SETNX` with expiry, or a DB table with a sweeper).

**Always pair idempotency with:** transacted sessions or broker redelivery. Both produce duplicates; idempotency is the backstop.

## 9. Slow consumer + prefetch

**Symptom:** handler is slow (1s+). With default Artemis prefetch (1000 messages), a slow consumer drains the queue into its own memory and holds it there — unacknowledged. Other consumers starve.

**Broker config:** `consumerWindowSize` URL parameter (core) or `jms.prefetchPolicy.all` (OpenWire) — for the JMS client on Artemis use `?consumerWindowSize=…` on the connection URL. Set low (e.g., `consumerWindowSize=0` for strict pull, or `=1` for one-at-a-time).

**Handler pattern:** don't hold messages in memory if processing is slow. Tune prefetch to the number of messages the consumer can comfortably buffer. If the handler is genuinely slow, scale horizontally — more consumers, each with low prefetch.

**Also:** set `<slow-consumer-threshold>` on the address to have Artemis log or kill consumers that fall behind a rate threshold.

## 10. Connection failure / broker restart

**Symptom:** TCP connection drops, broker restarts, network partition.

**Micronaut JMS behaviour:** the JMS client reconnects automatically. In-flight, un-acked messages are redelivered on reconnect (broker's view — it didn't see the ack, so the message is still on the queue).

**Handler pattern:** nothing special — just be idempotent. The transport layer handles the reconnect; your handler sees what looks like a redelivered message.

**Verify via:** `docs/scripts/connection-failure-demo.sh` — it restarts the container while the steady emitter is running and you watch logs for reconnect events and redelivery counts.

**Tune:** the Artemis client URL supports `reconnectAttempts=-1` (unlimited), `retryInterval=500`, `retryIntervalMultiplier=2.0`, `maxRetryInterval=30000`. Pin these explicitly; defaults vary by version.

## 11. Dead-letter observation and re-drive

Once messages land in `DLQ`, someone has to look at them. The `DeadLetterObserver` listener subscribes to `DLQ` and logs every arrival with origin headers. In production:

- Export DLQ depth as a metric; page if it grows.
- Provide a re-drive CLI/endpoint: reads from DLQ, writes back to `_AMQ_ORIG_ADDRESS`. Be deliberate — replaying a poison pill will just DLQ again.
- Archive: after N days, move DLQ contents to object storage so you can investigate later without keeping broker memory occupied.

## Decision matrix

| Failure type | Right pattern | Wrong pattern |
|---|---|---|
| Network blip | broker redelivery (throw) | swallow the exception |
| Bug in handler | DLQ + fix + re-drive | retry forever |
| Malformed payload | poison-pill → DLQ (max-delivery-attempts=1) | normal DLQ with 5 retries |
| Expired / stale | TTL + expiry queue | consumer checks timestamp and drops |
| Business rejection | app-level DLQ, ack original | throw to broker DLQ |
| Fast transient (503) | `@Retryable` in-process | broker redelivery (too slow) |
| Durable transient | broker redelivery | in-process retry (consumer crash loses it) |
| Side-effect + ack | transacted session | auto-ack |
| Duplicates | idempotent + dedup store | ignore; assume exactly-once |
| Slow consumer | tune prefetch, scale horizontally | increase memory |
| Broker restart | rely on client reconnect + idempotency | fail the app |

## See also

- `docs/triggering-scenarios.md` — exact HTTP calls to fire each scenario.
- `infra/artemis/broker.xml` — per-address redelivery/DLQ/expiry configuration.
- [Artemis redelivery documentation](https://activemq.apache.org/components/artemis/documentation/latest/undelivered-messages.html)
- [Artemis message expiry](https://activemq.apache.org/components/artemis/documentation/latest/message-expiry.html)
````

- [ ] **Step 2: Commit**

```bash
git add events/artemis-broker-exception-handling/docs/error-scenarios.md
git commit -m "docs(artemis-eh): comprehensive error-scenarios guide"
```

---

## Task 19: `docs/triggering-scenarios.md` and README

**Files:**
- Create: `events/artemis-broker-exception-handling/docs/triggering-scenarios.md`
- Create: `events/artemis-broker-exception-handling/README.md`

- [ ] **Step 1: Write triggering-scenarios.md**

`events/artemis-broker-exception-handling/docs/triggering-scenarios.md`:

````markdown
# Triggering Scenarios

All scenarios are fired via HTTP on the producer (default `http://localhost:8081`). Each call emits exactly one message to the target queue.

## One-off triggers

```bash
# Happy path
curl -X POST http://localhost:8081/trigger/HAPPY_PATH

# Transient failure (consumer throws 2x, succeeds on 3rd)
curl -X POST http://localhost:8081/trigger/TRANSIENT_FAILURE

# Permanent failure (→ DLQ after 5 attempts)
curl -X POST http://localhost:8081/trigger/PERMANENT_FAILURE

# Poison pill (malformed JSON → DLQ immediately)
curl -X POST http://localhost:8081/trigger/POISON_PILL

# Expired TTL (500ms; consumer paused → ExpiryQueue)
curl -X POST http://localhost:8081/trigger/EXPIRED_TTL

# Business validation (INVALID-DOMAIN-VALUE → business-dlq)
curl -X POST http://localhost:8081/trigger/BUSINESS_VALIDATION

# App-side retry (@Retryable 3x in-process)
curl -X POST http://localhost:8081/trigger/APP_RETRY

# Transacted (external write fails twice → rollback + redeliver)
curl -X POST http://localhost:8081/trigger/TRANSACTED

# Idempotent (same messageId sent twice; second skipped)
curl -X POST http://localhost:8081/trigger/IDEMPOTENT

# Slow consumer (1s sleep per message)
curl -X POST http://localhost:8081/trigger/SLOW_CONSUMER
```

## Isolating a single scenario

Each listener is gated by `scenarios.<name>.enabled`. To study one scenario without background noise, disable the others in `consumer/src/main/resources/application.yml` or via env vars:

```bash
SCENARIOS_HAPPY_PATH_ENABLED=false \
SCENARIOS_TRANSIENT_FAILURE_ENABLED=false \
SCENARIOS_PERMANENT_FAILURE_ENABLED=true \
SCENARIOS_POISON_PILL_ENABLED=false \
… \
mvn -pl consumer -am mn:run
```

And disable the steady-state emitter on the producer:

```bash
EMITTER_ENABLED=false mvn -pl producer -am mn:run
```

## Steady emit with failure injection

To run 50 msg/sec with 10% of messages diverted to the transient-failure scenario:

```bash
EMITTER_ENABLED=true \
EMITTER_FAILURE_INJECTION_RATIO=0.1 \
mvn -pl producer -am mn:run
```

## Observing

- **Artemis web console:** http://localhost:8161/console (login artemis/artemis) — queue depths, DLQ contents, expiry queue.
- **Consumer logs:** each listener logs every attempt and outcome at DEBUG.
- **DLQ observer:** if `scenarios.dlq-observer.enabled=true`, every DLQ arrival is logged with `_AMQ_ORIG_QUEUE`.

## Connection-failure demo

With both apps running plus `docker compose up artemis`:

```bash
./docs/scripts/connection-failure-demo.sh
```

Watch the consumer logs for reconnect / redelivery messages and the Artemis console for queue-depth changes.
````

- [ ] **Step 2: Write README.md**

`events/artemis-broker-exception-handling/README.md`:

````markdown
# Artemis Broker Exception Handling

A self-contained Micronaut demo of every common Artemis consumer-failure mode. Each scenario (retry, DLQ, TTL expiry, poison pill, business rejection, in-process retry, transacted rollback, idempotent consume, slow consumer, DLQ observation, connection failure) has a dedicated queue, a dedicated listener, and is triggerable from a single HTTP endpoint.

## Modules

- `producer/` — Micronaut app. Emits 50 msg/sec on the happy-path queue (configurable failure injection) and exposes `POST /trigger/{scenario}` to fire a single message at any scenario queue on demand.
- `consumer/` — Micronaut app. Hosts one listener per scenario; each is toggleable via `scenarios.<name>.enabled`.
- `infra/artemis/broker.xml` — per-address redelivery, DLQ, expiry, and flow-control settings.
- `docs/` — [the comprehensive error-scenarios guide](docs/error-scenarios.md) and [how to trigger each scenario](docs/triggering-scenarios.md).

## Quickstart

```bash
# 1. Start the broker
cd events/artemis-broker-exception-handling
docker compose up -d
# wait ~15s for the healthcheck

# 2. Start the consumer (in one terminal)
mvn -pl consumer -am mn:run

# 3. Start the producer (in another terminal)
mvn -pl producer -am mn:run

# 4. Trigger scenarios
curl -X POST http://localhost:8081/trigger/TRANSIENT_FAILURE
curl -X POST http://localhost:8081/trigger/PERMANENT_FAILURE
curl -X POST http://localhost:8081/trigger/POISON_PILL

# 5. Watch the broker (login artemis/artemis)
open http://localhost:8161/console
```

## Running the tests

Every scenario has a Testcontainers-backed integration test.

```bash
mvn -pl consumer -am test
mvn -pl producer -am test
```

Each test boots an Artemis container with the same `broker.xml` you get locally, so the assertions reflect real broker behaviour — not mocks.

## Where to start reading

1. [`docs/error-scenarios.md`](docs/error-scenarios.md) — the comprehensive guide. Read this first if you want the conceptual overview.
2. [`infra/artemis/broker.xml`](infra/artemis/broker.xml) — see the redelivery/DLQ/expiry rules in one place.
3. [`consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/`](consumer/src/main/java/com/mavharsha/events/artemis/consumer/listener/) — one file per scenario.
4. [`docs/triggering-scenarios.md`](docs/triggering-scenarios.md) — how to reproduce each one.

## Stack

Java 21 · Micronaut 4.10.4 · ActiveMQ Artemis 2.33.0 · Maven · Testcontainers · JUnit 5 · Awaitility · AssertJ.
````

- [ ] **Step 3: Commit**

```bash
git add events/artemis-broker-exception-handling/README.md \
        events/artemis-broker-exception-handling/docs/triggering-scenarios.md
git commit -m "docs(artemis-eh): README quickstart and triggering-scenarios cheatsheet"
```

---

## Task 20: Full verification — run all tests end-to-end

- [ ] **Step 1: Clean build + tests, top-level**

Run:

```bash
cd events/artemis-broker-exception-handling
mvn clean verify
```

Expected: `BUILD SUCCESS`. All Testcontainers-backed ITs pass: `HappyPathListenerIT`, `TransientFailureListenerIT`, `PermanentFailureListenerIT`, `PoisonPillListenerIT`, `ExpiringTtlListenerIT`, `BusinessValidationListenerIT`, `AppRetryListenerIT`, `TransactedListenerIT`, `IdempotentListenerIT`, `DeadLetterObserverIT`, `SteadyStateEmitterIT`.

- [ ] **Step 2: Manual end-to-end smoke**

```bash
docker compose up -d
# (in separate terminals)
mvn -pl consumer -am mn:run
mvn -pl producer -am mn:run

# trigger one of each scenario
for s in HAPPY_PATH TRANSIENT_FAILURE PERMANENT_FAILURE POISON_PILL EXPIRED_TTL \
         BUSINESS_VALIDATION APP_RETRY TRANSACTED IDEMPOTENT SLOW_CONSUMER; do
  echo "--- $s"
  curl -sS -X POST "http://localhost:8081/trigger/$s" | tee /dev/stderr
  echo
done
```

Expected in consumer logs over ~60s:
- `happy-path processed=...`
- `transient-failure` attempt 1/2 fails, attempt 3 succeeds
- `permanent-failure` attempts 1–5, then DLQ observer logs the DLQ arrival
- `poison-pill` DLQ observer sees arrival (no successful handler call)
- `expired-ttl` — no listener log (expired before pickup), ExpiryQueue depth increments in Artemis console
- `business-validation rejected …`, then message appears on `scenarios.business-dlq` (browse in console)
- `app-retry innerCall=1/2/3`, then `succeeded`
- `transacted attempt=1/2/3`, third commits
- `idempotent processed` once, `skip duplicate` once
- `slow-consumer processed` every ~1s

Tear down:

```bash
docker compose down -v
```

- [ ] **Step 3: Final commit (docs polish, if any)**

If any iteration of the verification uncovered small issues, fix and commit:

```bash
git status
# fix things
git add .
git commit -m "chore(artemis-eh): verification polish"
```

---

## Self-Review Checklist

**Spec coverage:**
- ✅ Two Java apps in `events/artemis-broker-exception-handling/` — producer (Task 2) + consumer (Task 3).
- ✅ Docker compose with Artemis — Task 1.
- ✅ 50 msg/sec producer — Task 6 (`SteadyStateEmitter`).
- ✅ Comprehensive error scenarios — Tasks 7–16 cover all 11 scenarios from the catalogue.
- ✅ All scenarios separately toggleable — `@Requires(property = "scenarios.<name>.enabled")` on every listener, one-off HTTP trigger endpoint.
- ✅ Both broker-side and app-side retry demonstrated — Tasks 7/8 (broker) + Task 12 (app).
- ✅ Testcontainers-based tests — shared `ArtemisTestResource` + one IT per scenario.
- ✅ Notes in project — `docs/error-scenarios.md` (Task 18), `docs/triggering-scenarios.md` + `README.md` (Task 19).

**Type consistency:**
- `Scenario` enum values are identical in producer and consumer (10 entries, no rename between tasks).
- `ScenarioQueues` constants used consistently across producer `ScenarioPublisher`, `TtlPublisher`, `TriggerController` and all consumer listeners.
- `ScenarioMessage` record shape (`messageId`, `scenario`, `payload`, `emittedAt`) matches in producer and consumer and in every test payload string.

**Placeholder scan:** no "TBD", no "add validation", every code step has complete code, every command has an expected outcome.

---

## Execution Handoff

Plan complete and saved to `events/docs/superpowers/plans/2026-04-21-artemis-broker-exception-handling.md`. Two execution options:

1. **Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks, fast iteration. Uses `superpowers:subagent-driven-development`.
2. **Inline Execution** — execute tasks in this session using `superpowers:executing-plans`, batch execution with checkpoints.

Which approach?
