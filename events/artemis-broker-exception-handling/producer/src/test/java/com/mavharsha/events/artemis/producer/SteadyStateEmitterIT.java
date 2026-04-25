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
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "emitter.enabled", value = "true")
@Property(name = "emitter.rate-per-second", value = "50")
class SteadyStateEmitterIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;

    @Override
    public Map<String, String> getProperties() {
        Map<String, String> props = new HashMap<>(super.getProperties());
        props.put("emitter.enabled", "true");
        return props;
    }

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
