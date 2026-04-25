package com.mavharsha.events.artemis.consumer;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.Message;
import jakarta.jms.Queue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PoisonPillListenerIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;

    @Test
    void malformed_payload_routes_to_dlq() throws Exception {
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue q = ctx.createQueue("scenarios.poison-pill");
            ctx.createProducer().send(q, ctx.createTextMessage("{ malformed json"));
        }

        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue dlq = ctx.createQueue("DLQ");
            JMSConsumer consumer = ctx.createConsumer(dlq);
            Message dlqMsg = consumer.receive(15_000);
            assertThat(dlqMsg).isNotNull();
            assertThat(dlqMsg.getBody(String.class)).contains("malformed");
        }
    }
}
