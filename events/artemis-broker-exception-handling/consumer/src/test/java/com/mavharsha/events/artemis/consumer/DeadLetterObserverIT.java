package com.mavharsha.events.artemis.consumer;

import com.mavharsha.events.artemis.consumer.listener.DeadLetterObserver;
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
