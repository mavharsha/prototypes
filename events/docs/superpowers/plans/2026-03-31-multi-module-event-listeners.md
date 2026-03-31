# Multi-Module Event Listeners Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure the SQS listener project into a Maven multi-module project with SQS, Kafka, and ActiveMQ Artemis listener modules, each producing an independent Docker image.

**Architecture:** Maven parent POM at `events/` with three child modules (`listener-sqs`, `listener-kafka`, `listener-activemq`). Each module uses Micronaut's idiomatic messaging annotations instead of `@Scheduled` polling. A single `docker-compose.yml` provides all three brokers for local dev. Multi-stage Dockerfiles produce slim JRE images per module.

**Tech Stack:** Java 21, Micronaut 4.10.4, Maven, Docker (multi-stage), LocalStack (SQS), Bitnami Kafka (KRaft), Apache ActiveMQ Artemis

**Spec:** `docs/superpowers/specs/2026-03-31-multi-module-event-listeners-design.md`

---

### Task 1: Create Parent POM and Module Directory Structure

**Files:**
- Create: `events/pom.xml`
- Create: `events/listener-sqs/pom.xml`
- Create: `events/listener-kafka/pom.xml`
- Create: `events/listener-activemq/pom.xml`

- [ ] **Step 1: Create the parent POM at `events/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.mavharsha</groupId>
  <artifactId>events-parent</artifactId>
  <version>0.1</version>
  <packaging>pom</packaging>

  <parent>
    <groupId>io.micronaut.platform</groupId>
    <artifactId>micronaut-parent</artifactId>
    <version>4.10.4</version>
  </parent>

  <modules>
    <module>listener-sqs</module>
    <module>listener-kafka</module>
    <module>listener-activemq</module>
  </modules>

  <properties>
    <jdk.version>21</jdk.version>
    <release.version>21</release.version>
    <micronaut.version>4.10.4</micronaut.version>
    <micronaut.runtime>netty</micronaut.runtime>
    <micronaut.aot.enabled>false</micronaut.aot.enabled>
  </properties>

  <repositories>
    <repository>
      <id>central</id>
      <url>https://repo.maven.apache.org/maven2</url>
    </repository>
  </repositories>

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.micronaut</groupId>
        <artifactId>micronaut-http-server-netty</artifactId>
      </dependency>
      <dependency>
        <groupId>io.micronaut.serde</groupId>
        <artifactId>micronaut-serde-jackson</artifactId>
      </dependency>
    </dependencies>
  </dependencyManagement>

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
  </dependencies>

  <build>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>io.micronaut.maven</groupId>
          <artifactId>micronaut-maven-plugin</artifactId>
          <configuration>
            <configFile>aot-${packaging}.properties</configFile>
          </configuration>
        </plugin>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-compiler-plugin</artifactId>
          <configuration>
            <annotationProcessorPaths combine.children="append">
              <path>
                <groupId>io.micronaut</groupId>
                <artifactId>micronaut-http-validation</artifactId>
                <version>${micronaut.core.version}</version>
              </path>
              <path>
                <groupId>io.micronaut.serde</groupId>
                <artifactId>micronaut-serde-processor</artifactId>
                <version>${micronaut.serialization.version}</version>
                <exclusions>
                  <exclusion>
                    <groupId>io.micronaut</groupId>
                    <artifactId>micronaut-inject</artifactId>
                  </exclusion>
                </exclusions>
              </path>
            </annotationProcessorPaths>
          </configuration>
        </plugin>
      </plugins>
    </pluginManagement>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-enforcer-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Create `events/listener-sqs/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.mavharsha</groupId>
    <artifactId>events-parent</artifactId>
    <version>0.1</version>
  </parent>

  <artifactId>listener-sqs</artifactId>
  <packaging>${packaging}</packaging>

  <properties>
    <packaging>jar</packaging>
    <micronaut.aot.packageName>com.mavharsha.events.sqs.aot.generated</micronaut.aot.packageName>
    <exec.mainClass>com.mavharsha.events.sqs.Application</exec.mainClass>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.micronaut.aws</groupId>
      <artifactId>micronaut-aws-messaging</artifactId>
      <scope>compile</scope>
    </dependency>
    <dependency>
      <groupId>software.amazon.awssdk</groupId>
      <artifactId>sqs</artifactId>
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
            <arg>-Amicronaut.processing.module=listener-sqs</arg>
          </compilerArgs>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 3: Create `events/listener-kafka/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.mavharsha</groupId>
    <artifactId>events-parent</artifactId>
    <version>0.1</version>
  </parent>

  <artifactId>listener-kafka</artifactId>
  <packaging>${packaging}</packaging>

  <properties>
    <packaging>jar</packaging>
    <micronaut.aot.packageName>com.mavharsha.events.kafka.aot.generated</micronaut.aot.packageName>
    <exec.mainClass>com.mavharsha.events.kafka.Application</exec.mainClass>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.micronaut.kafka</groupId>
      <artifactId>micronaut-kafka</artifactId>
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
            <arg>-Amicronaut.processing.module=listener-kafka</arg>
          </compilerArgs>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 4: Create `events/listener-activemq/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.mavharsha</groupId>
    <artifactId>events-parent</artifactId>
    <version>0.1</version>
  </parent>

  <artifactId>listener-activemq</artifactId>
  <packaging>${packaging}</packaging>

  <properties>
    <packaging>jar</packaging>
    <micronaut.aot.packageName>com.mavharsha.events.activemq.aot.generated</micronaut.aot.packageName>
    <exec.mainClass>com.mavharsha.events.activemq.Application</exec.mainClass>
  </properties>

  <dependencies>
    <dependency>
      <groupId>io.micronaut.jms</groupId>
      <artifactId>micronaut-jms-activemq-artemis</artifactId>
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
            <arg>-Amicronaut.processing.module=listener-activemq</arg>
          </compilerArgs>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 5: Create directory structure for all three modules**

```bash
mkdir -p events/listener-sqs/src/main/java/com/mavharsha/events/sqs/listener
mkdir -p events/listener-sqs/src/main/resources
mkdir -p events/listener-kafka/src/main/java/com/mavharsha/events/kafka/listener
mkdir -p events/listener-kafka/src/main/resources
mkdir -p events/listener-activemq/src/main/java/com/mavharsha/events/activemq/listener
mkdir -p events/listener-activemq/src/main/resources
mkdir -p events/infra/localstack
mkdir -p events/infra/artemis
mkdir -p events/scripts
```

- [ ] **Step 6: Verify parent POM resolves**

Run: `cd events && mvn validate`
Expected: `BUILD SUCCESS` (modules don't have source yet, but POM structure should resolve)

- [ ] **Step 7: Commit**

```bash
git add events/pom.xml events/listener-sqs/pom.xml events/listener-kafka/pom.xml events/listener-activemq/pom.xml
git commit -m "feat: add parent POM and module POMs for multi-module event listeners"
```

---

### Task 2: Implement listener-sqs Module

**Files:**
- Create: `events/listener-sqs/src/main/java/com/mavharsha/events/sqs/Application.java`
- Create: `events/listener-sqs/src/main/java/com/mavharsha/events/sqs/listener/SqsListenerService.java`
- Create: `events/listener-sqs/src/main/resources/application.yml`
- Create: `events/listener-sqs/src/main/resources/logback.xml`
- Copy: `events/listener-sqs/aot-jar.properties` (from `micronaut-sqs/aot-jar.properties`)

- [ ] **Step 1: Create `events/listener-sqs/src/main/java/com/mavharsha/events/sqs/Application.java`**

```java
package com.mavharsha.events.sqs;

import io.micronaut.runtime.Micronaut;

public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
```

- [ ] **Step 2: Create `events/listener-sqs/src/main/java/com/mavharsha/events/sqs/listener/SqsListenerService.java`**

```java
package com.mavharsha.events.sqs.listener;

import io.micronaut.aws.sqs.annotation.SqsListener;
import io.micronaut.messaging.annotation.MessageBody;
import io.micronaut.messaging.annotation.MessageHeader;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SqsListenerService {

    private static final Logger LOG = LoggerFactory.getLogger(SqsListenerService.class);

    @SqsListener("events-queue")
    void onMessage(@MessageBody String body, @MessageHeader("MessageId") String messageId) {
        LOG.info("Received SQS message [{}]: {}", messageId, body);
    }
}
```

Note: The exact import paths for `@SqsListener` may differ depending on the `micronaut-aws-messaging` version. If `io.micronaut.aws.sqs.annotation.SqsListener` does not exist, check the library's actual package structure and adjust. The Micronaut AWS messaging module is relatively new — verify the annotation exists by checking the dependency's JAR after `mvn dependency:resolve`.

- [ ] **Step 3: Create `events/listener-sqs/src/main/resources/application.yml`**

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

- [ ] **Step 4: Create `events/listener-sqs/src/main/resources/logback.xml`**

```xml
<configuration>

    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%cyan(%d{HH:mm:ss.SSS}) %gray([%thread]) %highlight(%-5level) %magenta(%logger{36}) - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="com.mavharsha.events" level="DEBUG"/>

    <root level="info">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

- [ ] **Step 5: Copy `aot-jar.properties` from the existing project**

```bash
cp events/micronaut-sqs/aot-jar.properties events/listener-sqs/aot-jar.properties
```

- [ ] **Step 6: Build the module**

Run: `cd events && mvn -pl listener-sqs -am compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add events/listener-sqs/
git commit -m "feat: add listener-sqs module with @SqsListener"
```

---

### Task 3: Implement listener-kafka Module

**Files:**
- Create: `events/listener-kafka/src/main/java/com/mavharsha/events/kafka/Application.java`
- Create: `events/listener-kafka/src/main/java/com/mavharsha/events/kafka/listener/KafkaListenerService.java`
- Create: `events/listener-kafka/src/main/resources/application.yml`
- Create: `events/listener-kafka/src/main/resources/logback.xml`
- Copy: `events/listener-kafka/aot-jar.properties`

- [ ] **Step 1: Create `events/listener-kafka/src/main/java/com/mavharsha/events/kafka/Application.java`**

```java
package com.mavharsha.events.kafka;

import io.micronaut.runtime.Micronaut;

public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
```

- [ ] **Step 2: Create `events/listener-kafka/src/main/java/com/mavharsha/events/kafka/listener/KafkaListenerService.java`**

```java
package com.mavharsha.events.kafka.listener;

import io.micronaut.configuration.kafka.annotation.KafkaListener;
import io.micronaut.configuration.kafka.annotation.Topic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@KafkaListener(groupId = "events-listener")
public class KafkaListenerService {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaListenerService.class);

    @Topic("events-topic")
    void onMessage(String body) {
        LOG.info("Received Kafka message: {}", body);
    }
}
```

- [ ] **Step 3: Create `events/listener-kafka/src/main/resources/application.yml`**

```yaml
micronaut:
  application:
    name: listener-kafka

kafka:
  bootstrap:
    servers: localhost:9092
```

- [ ] **Step 4: Create `events/listener-kafka/src/main/resources/logback.xml`**

```xml
<configuration>

    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%cyan(%d{HH:mm:ss.SSS}) %gray([%thread]) %highlight(%-5level) %magenta(%logger{36}) - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="com.mavharsha.events" level="DEBUG"/>

    <root level="info">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

- [ ] **Step 5: Copy `aot-jar.properties`**

```bash
cp events/micronaut-sqs/aot-jar.properties events/listener-kafka/aot-jar.properties
```

- [ ] **Step 6: Build the module**

Run: `cd events && mvn -pl listener-kafka -am compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add events/listener-kafka/
git commit -m "feat: add listener-kafka module with @KafkaListener"
```

---

### Task 4: Implement listener-activemq Module

**Files:**
- Create: `events/listener-activemq/src/main/java/com/mavharsha/events/activemq/Application.java`
- Create: `events/listener-activemq/src/main/java/com/mavharsha/events/activemq/listener/ActiveMqListenerService.java`
- Create: `events/listener-activemq/src/main/resources/application.yml`
- Create: `events/listener-activemq/src/main/resources/logback.xml`
- Copy: `events/listener-activemq/aot-jar.properties`

- [ ] **Step 1: Create `events/listener-activemq/src/main/java/com/mavharsha/events/activemq/Application.java`**

```java
package com.mavharsha.events.activemq;

import io.micronaut.runtime.Micronaut;

public class Application {
    public static void main(String[] args) {
        Micronaut.run(Application.class, args);
    }
}
```

- [ ] **Step 2: Create `events/listener-activemq/src/main/java/com/mavharsha/events/activemq/listener/ActiveMqListenerService.java`**

```java
package com.mavharsha.events.activemq.listener;

import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class ActiveMqListenerService {

    private static final Logger LOG = LoggerFactory.getLogger(ActiveMqListenerService.class);

    @Queue("events-queue")
    void onMessage(@MessageBody String body) {
        LOG.info("Received ActiveMQ message: {}", body);
    }
}
```

Note: `@JMSListener` takes the connection factory bean name, not a string literal like `"activemq"`. The Micronaut JMS Artemis module provides `ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME` for this purpose.

- [ ] **Step 3: Create `events/listener-activemq/src/main/resources/application.yml`**

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

- [ ] **Step 4: Create `events/listener-activemq/src/main/resources/logback.xml`**

```xml
<configuration>

    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%cyan(%d{HH:mm:ss.SSS}) %gray([%thread]) %highlight(%-5level) %magenta(%logger{36}) - %msg%n</pattern>
        </encoder>
    </appender>

    <logger name="com.mavharsha.events" level="DEBUG"/>

    <root level="info">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

- [ ] **Step 5: Copy `aot-jar.properties`**

```bash
cp events/micronaut-sqs/aot-jar.properties events/listener-activemq/aot-jar.properties
```

- [ ] **Step 6: Build the module**

Run: `cd events && mvn -pl listener-activemq -am compile`
Expected: `BUILD SUCCESS`

- [ ] **Step 7: Commit**

```bash
git add events/listener-activemq/
git commit -m "feat: add listener-activemq module with @JMSListener for Artemis"
```

---

### Task 5: Create docker-compose.yml with All Three Brokers

**Files:**
- Create: `events/docker-compose.yml`
- Create: `events/infra/localstack/init-sqs.sh`
- Create: `events/infra/artemis/create-queue.sh`

- [ ] **Step 1: Create `events/infra/localstack/init-sqs.sh`**

```bash
#!/bin/bash
echo "Creating SQS queue: events-queue"
awslocal sqs create-queue --queue-name events-queue
echo "SQS queue created successfully"
```

- [ ] **Step 2: Create `events/infra/artemis/create-queue.sh`**

```bash
#!/bin/bash
echo "Creating Artemis queue: events-queue"
/var/lib/artemis-instance/bin/artemis queue create \
  --name events-queue \
  --address events-queue \
  --anycast \
  --no-durable \
  --auto-create-address \
  --user artemis \
  --password artemis \
  --silent
echo "Artemis queue created successfully"
```

- [ ] **Step 3: Make init scripts executable**

```bash
chmod +x events/infra/localstack/init-sqs.sh
chmod +x events/infra/artemis/create-queue.sh
```

- [ ] **Step 4: Create `events/docker-compose.yml`**

```yaml
services:
  localstack:
    image: localstack/localstack:4.4
    ports:
      - "4566:4566"
    environment:
      SERVICES: sqs
      DEBUG: 0
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
    ports:
      - "9092:9092"
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
      - "61616:61616"
      - "8161:8161"
    environment:
      ARTEMIS_USER: artemis
      ARTEMIS_PASSWORD: artemis
    volumes:
      - ./infra/artemis/create-queue.sh:/var/lib/artemis-instance/etc/artemis-init.sh
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8161/console"]
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 15s
```

- [ ] **Step 5: Start the brokers and verify health**

Run: `cd events && docker compose up -d`
Then: `docker compose ps`
Expected: All three services show `healthy` status (may take 15-30 seconds)

- [ ] **Step 6: Commit**

```bash
git add events/docker-compose.yml events/infra/
git commit -m "feat: add docker-compose with LocalStack, Kafka, and Artemis"
```

---

### Task 6: Create Dockerfiles for Each Module

**Files:**
- Create: `events/listener-sqs/Dockerfile`
- Create: `events/listener-kafka/Dockerfile`
- Create: `events/listener-activemq/Dockerfile`

- [ ] **Step 1: Create `events/listener-sqs/Dockerfile`**

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
RUN apk add --no-cache maven
WORKDIR /app
COPY pom.xml ./pom.xml
COPY listener-sqs/pom.xml ./listener-sqs/pom.xml
COPY listener-sqs/aot-jar.properties ./listener-sqs/aot-jar.properties
RUN mvn -pl listener-sqs -am dependency:resolve -q
COPY listener-sqs/src ./listener-sqs/src
RUN mvn -pl listener-sqs -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/listener-sqs/target/listener-sqs-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Create `events/listener-kafka/Dockerfile`**

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
RUN apk add --no-cache maven
WORKDIR /app
COPY pom.xml ./pom.xml
COPY listener-kafka/pom.xml ./listener-kafka/pom.xml
COPY listener-kafka/aot-jar.properties ./listener-kafka/aot-jar.properties
RUN mvn -pl listener-kafka -am dependency:resolve -q
COPY listener-kafka/src ./listener-kafka/src
RUN mvn -pl listener-kafka -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/listener-kafka/target/listener-kafka-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 3: Create `events/listener-activemq/Dockerfile`**

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
RUN apk add --no-cache maven
WORKDIR /app
COPY pom.xml ./pom.xml
COPY listener-activemq/pom.xml ./listener-activemq/pom.xml
COPY listener-activemq/aot-jar.properties ./listener-activemq/aot-jar.properties
RUN mvn -pl listener-activemq -am dependency:resolve -q
COPY listener-activemq/src ./listener-activemq/src
RUN mvn -pl listener-activemq -am package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/listener-activemq/target/listener-activemq-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 4: Build all three Docker images**

Run from `events/` directory:
```bash
docker build -f listener-sqs/Dockerfile -t events/listener-sqs:latest .
docker build -f listener-kafka/Dockerfile -t events/listener-kafka:latest .
docker build -f listener-activemq/Dockerfile -t events/listener-activemq:latest .
```
Expected: All three builds succeed and produce images

- [ ] **Step 5: Verify images exist**

Run: `docker images | grep events/listener`
Expected: Three images listed (`events/listener-sqs`, `events/listener-kafka`, `events/listener-activemq`)

- [ ] **Step 6: Commit**

```bash
git add events/listener-sqs/Dockerfile events/listener-kafka/Dockerfile events/listener-activemq/Dockerfile
git commit -m "feat: add multi-stage Dockerfiles for all three listener modules"
```

---

### Task 7: Create Publish Scripts

**Files:**
- Create: `events/scripts/publish-sqs.sh`
- Create: `events/scripts/publish-kafka.sh`
- Create: `events/scripts/publish-activemq.sh`

- [ ] **Step 1: Create `events/scripts/publish-sqs.sh`**

```bash
#!/bin/bash
ENDPOINT_URL="http://localhost:4566"
REGION="us-east-1"
QUEUE_URL="http://sqs.us-east-1.localhost.localstack.cloud:4566/000000000000/events-queue"

MESSAGE_BODY="${1:-'{\"event\":\"order.created\",\"orderId\":\"ORD-12345\",\"timestamp\":\"2026-03-31T12:00:00Z\"}'}"

echo "Publishing message to SQS queue..."
aws --endpoint-url="$ENDPOINT_URL" --region "$REGION" \
    sqs send-message \
    --queue-url "$QUEUE_URL" \
    --message-body "$MESSAGE_BODY"
```

- [ ] **Step 2: Create `events/scripts/publish-kafka.sh`**

```bash
#!/bin/bash
TOPIC="events-topic"
BROKER="localhost:9092"

MESSAGE_BODY="${1:-'{\"event\":\"order.created\",\"orderId\":\"ORD-12345\",\"timestamp\":\"2026-03-31T12:00:00Z\"}'}"

echo "Publishing message to Kafka topic: $TOPIC"
echo "$MESSAGE_BODY" | docker exec -i "$(docker compose ps -q kafka)" \
    kafka-console-producer.sh \
    --broker-list "$BROKER" \
    --topic "$TOPIC"
echo "Message published successfully"
```

- [ ] **Step 3: Create `events/scripts/publish-activemq.sh`**

```bash
#!/bin/bash
QUEUE="events-queue"
BROKER_URL="http://localhost:8161"
USER="artemis"
PASS="artemis"

MESSAGE_BODY="${1:-'{\"event\":\"order.created\",\"orderId\":\"ORD-12345\",\"timestamp\":\"2026-03-31T12:00:00Z\"}'}"

echo "Publishing message to Artemis queue: $QUEUE"
curl -s -u "$USER:$PASS" \
    -H "Content-Type: application/json" \
    -d "{\"type\":\"exec\",\"mbean\":\"org.apache.activemq.artemis:broker=\\\"amq-broker\\\",component=addresses,address=\\\"$QUEUE\\\",subcomponent=queues,routing-type=\\\"anycast\\\",queue=\\\"$QUEUE\\\"\",\"operation\":\"sendMessage(java.util.Map,int,java.lang.String,boolean,java.lang.String,java.lang.String)\",\"arguments\":[{},3,\"$MESSAGE_BODY\",true,null,null]}" \
    "$BROKER_URL/console/jolokia/exec"
echo ""
echo "Message published successfully"
```

Note: The Artemis Jolokia MBean path and broker name may vary depending on the Docker image version. If the curl command returns an error, check the actual broker name by visiting `http://localhost:8161/console` and inspecting the JMX tree. An alternative is to use the Artemis CLI inside the container:

```bash
docker exec "$(docker compose ps -q artemis)" \
    /var/lib/artemis-instance/bin/artemis producer \
    --destination "events-queue" \
    --message-count 1 \
    --message "$MESSAGE_BODY" \
    --user artemis \
    --password artemis
```

- [ ] **Step 4: Make scripts executable**

```bash
chmod +x events/scripts/publish-sqs.sh
chmod +x events/scripts/publish-kafka.sh
chmod +x events/scripts/publish-activemq.sh
```

- [ ] **Step 5: Commit**

```bash
git add events/scripts/
git commit -m "feat: add publish scripts for SQS, Kafka, and ActiveMQ"
```

---

### Task 8: Remove Old micronaut-sqs Project

**Files:**
- Remove: `events/micronaut-sqs/` (entire directory)

- [ ] **Step 1: Verify all new modules compile**

Run: `cd events && mvn clean compile`
Expected: `BUILD SUCCESS` for all three modules

- [ ] **Step 2: Remove the old project directory**

```bash
rm -rf events/micronaut-sqs
```

- [ ] **Step 3: Verify the full build still succeeds**

Run: `cd events && mvn clean compile`
Expected: `BUILD SUCCESS` (no references to old project)

- [ ] **Step 4: Commit**

```bash
git rm -r events/micronaut-sqs
git commit -m "chore: remove old micronaut-sqs project, replaced by listener-sqs module"
```

---

### Task 9: End-to-End Verification

- [ ] **Step 1: Build all modules from root**

Run: `cd events && mvn clean package -DskipTests`
Expected: `BUILD SUCCESS` with three JARs produced in each module's `target/` directory

- [ ] **Step 2: Start all brokers**

Run: `cd events && docker compose up -d`
Wait for healthy: `docker compose ps` (all three services should show `healthy`)

- [ ] **Step 3: Test SQS listener**

Terminal 1: `cd events && mvn -pl listener-sqs mn:run`
Terminal 2: `cd events && ./scripts/publish-sqs.sh`
Expected: Terminal 1 logs `Received SQS message [<id>]: {"event":"order.created",...}`

- [ ] **Step 4: Test Kafka listener**

Terminal 1: `cd events && mvn -pl listener-kafka mn:run`
Terminal 2: `cd events && ./scripts/publish-kafka.sh`
Expected: Terminal 1 logs `Received Kafka message: {"event":"order.created",...}`

- [ ] **Step 5: Test ActiveMQ listener**

Terminal 1: `cd events && mvn -pl listener-activemq mn:run`
Terminal 2: `cd events && ./scripts/publish-activemq.sh`
Expected: Terminal 1 logs `Received ActiveMQ message: {"event":"order.created",...}`

- [ ] **Step 6: Test Docker image builds**

```bash
cd events
docker build -f listener-sqs/Dockerfile -t events/listener-sqs:latest .
docker build -f listener-kafka/Dockerfile -t events/listener-kafka:latest .
docker build -f listener-activemq/Dockerfile -t events/listener-activemq:latest .
```
Expected: All three images build successfully

- [ ] **Step 7: Final commit (if any fixes were needed)**

```bash
git add -A
git commit -m "fix: address issues found during end-to-end verification"
```
