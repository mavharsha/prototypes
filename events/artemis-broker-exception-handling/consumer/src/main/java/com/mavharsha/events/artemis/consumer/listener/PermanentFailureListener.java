package com.mavharsha.events.artemis.consumer.listener;

import com.mavharsha.events.artemis.consumer.error.FailureCounter;
import com.mavharsha.events.artemis.consumer.model.ScenarioMessage;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.permanent-failure.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class PermanentFailureListener {

    private static final Logger LOG = LoggerFactory.getLogger(PermanentFailureListener.class);

    private final FailureCounter counter;

    public PermanentFailureListener(FailureCounter counter) {
        this.counter = counter;
    }

    @Queue(value = "scenarios.permanent-failure", transacted = true)
    void onMessage(@MessageBody ScenarioMessage message) {
        int attempt = counter.recordAttempt(message.messageId());
        LOG.warn("permanent-failure attempt={} id={}", attempt, message.messageId());
        throw new RuntimeException("permanent failure; this will never succeed");
    }

    public int attemptsFor(String messageId) {
        return counter.attemptsFor(messageId);
    }
}
