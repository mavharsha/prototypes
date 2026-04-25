package com.mavharsha.events.artemis.consumer;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Property(name = "scenarios.expired-ttl.enabled", value = "false")
class ExpiringTtlListenerIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;

    @Test
    void expired_message_routes_to_expiry_queue() throws Exception {
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue q = ctx.createQueue("scenarios.expired-ttl");
            JMSProducer producer = ctx.createProducer().setTimeToLive(500);
            producer.send(q, ctx.createTextMessage(
                    "{\"messageId\":\"ttl-1\",\"scenario\":\"EXPIRED_TTL\"," +
                    "\"payload\":\"x\",\"emittedAt\":\"2026-04-21T00:00:00Z\"}"));
        }

        Thread.sleep(1500);

        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue expiry = ctx.createQueue("ExpiryQueue");
            JMSConsumer consumer = ctx.createConsumer(expiry);
            Message m = consumer.receive(10_000);
            assertThat(m).isNotNull();
            assertThat(m.getBody(String.class)).contains("ttl-1");
        }
    }
}
