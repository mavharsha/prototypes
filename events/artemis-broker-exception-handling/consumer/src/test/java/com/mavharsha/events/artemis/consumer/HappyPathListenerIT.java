package com.mavharsha.events.artemis.consumer;

import com.mavharsha.events.artemis.consumer.listener.HappyPathListener;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;
import jakarta.jms.TextMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
