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
