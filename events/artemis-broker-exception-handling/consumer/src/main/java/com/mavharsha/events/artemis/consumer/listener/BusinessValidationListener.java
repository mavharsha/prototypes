package com.mavharsha.events.artemis.consumer.listener;

import com.mavharsha.events.artemis.consumer.model.ScenarioMessage;
import com.mavharsha.events.artemis.consumer.model.ScenarioQueues;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.business-validation.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class BusinessValidationListener {

    private static final Logger LOG = LoggerFactory.getLogger(BusinessValidationListener.class);

    private final ConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;

    public BusinessValidationListener(@Named(CONNECTION_FACTORY_BEAN_NAME) ConnectionFactory connectionFactory,
                                      ObjectMapper objectMapper) {
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
    }

    @Queue("scenarios.business-validation")
    void onMessage(@MessageBody ScenarioMessage message) throws Exception {
        if (!"VALID".equals(message.payload()) && !message.payload().startsWith("ok-")) {
            LOG.warn("business-validation rejected id={} payload={}", message.messageId(), message.payload());
            rejectToBusinessDlq(message, "INVALID_DOMAIN");
            return;
        }
        LOG.info("business-validation accepted id={}", message.messageId());
    }

    private void rejectToBusinessDlq(ScenarioMessage message, String reason) throws Exception {
        String body = new String(objectMapper.writeValueAsBytes(message));
        try (JMSContext ctx = connectionFactory.createContext(JMSContext.AUTO_ACKNOWLEDGE)) {
            TextMessage m = ctx.createTextMessage(body);
            m.setStringProperty("rejection_reason", reason);
            m.setStringProperty("original_queue", ScenarioQueues.BUSINESS_VALIDATION);
            JMSProducer producer = ctx.createProducer();
            producer.send(ctx.createQueue(ScenarioQueues.BUSINESS_DLQ), m);
        }
    }
}
