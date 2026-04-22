package com.mavharsha.events.artemis.producer.publish;

import com.mavharsha.events.artemis.producer.model.ScenarioMessage;
import com.mavharsha.events.artemis.producer.model.ScenarioQueues;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.Queue;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
public class TtlPublisher {

    private final ConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    public TtlPublisher(@Named(CONNECTION_FACTORY_BEAN_NAME) ConnectionFactory connectionFactory,
                        ObjectMapper objectMapper) {
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
    }

    public void sendExpiring(ScenarioMessage message, long ttlMillis) throws Exception {
        String body = new String(objectMapper.writeValueAsBytes(message));
        try (JMSContext ctx = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
            Queue q = ctx.createQueue(ScenarioQueues.EXPIRED_TTL);
            JMSProducer producer = ctx.createProducer().setTimeToLive(ttlMillis);
            producer.send(q, ctx.createTextMessage(body));
        }
    }
}
