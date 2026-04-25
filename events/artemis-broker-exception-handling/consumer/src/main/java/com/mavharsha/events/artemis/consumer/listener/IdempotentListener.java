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
