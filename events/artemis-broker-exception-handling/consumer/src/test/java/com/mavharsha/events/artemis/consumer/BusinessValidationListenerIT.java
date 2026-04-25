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
class BusinessValidationListenerIT extends ArtemisTestResource {

    @Inject ConnectionFactory connectionFactory;

    @Test
    void invalid_payload_routes_to_business_dlq_without_broker_redelivery() throws Exception {
        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue q = ctx.createQueue("scenarios.business-validation");
            ctx.createProducer().send(q, ctx.createTextMessage(
                    "{\"messageId\":\"bv-1\",\"scenario\":\"BUSINESS_VALIDATION\"," +
                    "\"payload\":\"INVALID-DOMAIN-VALUE\",\"emittedAt\":\"2026-04-21T00:00:00Z\"}"));
        }

        try (JMSContext ctx = connectionFactory.createContext()) {
            Queue bdlq = ctx.createQueue("scenarios.business-dlq");
            JMSConsumer consumer = ctx.createConsumer(bdlq);
            Message m = consumer.receive(10_000);
            assertThat(m).isNotNull();
            assertThat(m.getBody(String.class)).contains("bv-1");
            assertThat(m.getStringProperty("rejection_reason")).isEqualTo("INVALID_DOMAIN");
        }
    }
}
