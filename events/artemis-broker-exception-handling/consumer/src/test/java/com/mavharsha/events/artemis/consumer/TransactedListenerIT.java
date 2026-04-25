package com.mavharsha.events.artemis.consumer;

import com.mavharsha.events.artemis.consumer.external.ExternalWriteService;
import com.mavharsha.events.artemis.consumer.listener.TransactedListener;
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
class TransactedListenerIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;
    @Inject TransactedListener listener;
    @Inject ExternalWriteService external;

    @Test
    void rollback_causes_redelivery_and_no_partial_write() {
        external.failNextN(2); // first 2 attempts fail → session rolls back → redelivery
        String id = "tx-1";
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue q = ctx.createQueue("scenarios.transacted");
            ctx.createProducer().send(q, ctx.createTextMessage(
                    "{\"messageId\":\"" + id + "\",\"scenario\":\"TRANSACTED\"," +
                    "\"payload\":\"x\",\"emittedAt\":\"2026-04-21T00:00:00Z\"}"));
        }

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(listener.attempts()).isGreaterThanOrEqualTo(3);
            assertThat(external.committedCount()).isEqualTo(1); // only the successful attempt committed
        });
    }
}
