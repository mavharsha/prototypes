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
