package com.mavharsha.events.artemis.consumer;

import com.mavharsha.events.artemis.consumer.listener.AppRetryListener;
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
class AppRetryListenerIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;
    @Inject AppRetryListener listener;

    @Test
    void retries_in_process_without_broker_redelivery() {
        String id = "app-retry-1";
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue q = ctx.createQueue("scenarios.app-retry");
            ctx.createProducer().send(q, ctx.createTextMessage(
                    "{\"messageId\":\"" + id + "\",\"scenario\":\"APP_RETRY\"," +
                    "\"payload\":\"x\",\"emittedAt\":\"2026-04-21T00:00:00Z\"}"));
        }

        await().atMost(Duration.ofSeconds(15))
               .untilAsserted(() -> {
                   // handler invoked once at the listener boundary,
                   // but inner method invoked >= 3 times (initial + 2 retries)
                   assertThat(listener.handlerInvocations()).isEqualTo(1);
                   assertThat(listener.innerInvocations()).isGreaterThanOrEqualTo(3);
                   assertThat(listener.succeeded()).isTrue();
               });
    }
}
