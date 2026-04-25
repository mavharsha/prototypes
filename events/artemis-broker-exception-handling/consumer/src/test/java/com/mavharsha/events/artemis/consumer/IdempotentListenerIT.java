package com.mavharsha.events.artemis.consumer;

import com.mavharsha.events.artemis.consumer.listener.IdempotentListener;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
