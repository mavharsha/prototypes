package com.mavharsha.events.artemis.consumer;

import com.mavharsha.events.artemis.consumer.listener.TransientFailureListener;
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
