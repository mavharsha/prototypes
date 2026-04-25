package com.mavharsha.events.artemis.consumer;

import com.mavharsha.events.artemis.consumer.listener.PermanentFailureListener;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PermanentFailureListenerIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;
    @Inject PermanentFailureListener listener;

    @Test
    void lands_in_dlq_after_max_delivery_attempts() throws Exception {
        String id = "permanent-1";
        String body = "{\"messageId\":\"" + id + "\",\"scenario\":\"PERMANENT_FAILURE\"," +
                "\"payload\":\"x\",\"emittedAt\":\"2026-04-21T00:00:00Z\"}";
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue q = ctx.createQueue("scenarios.permanent-failure");
            ctx.createProducer().send(q, ctx.createTextMessage(body));
        }

        await().atMost(Duration.ofSeconds(60))
               .pollInterval(Duration.ofSeconds(1))
               .untilAsserted(() -> assertThat(listener.attemptsFor(id)).isGreaterThanOrEqualTo(5));

        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue dlq = ctx.createQueue("DLQ");
            JMSConsumer consumer = ctx.createConsumer(dlq);
            Message dlqMsg = consumer.receive(10_000);
            assertThat(dlqMsg).isNotNull();
            assertThat(dlqMsg.getBody(String.class)).contains(id);
        }
    }
}
