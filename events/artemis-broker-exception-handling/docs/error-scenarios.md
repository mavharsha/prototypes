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
