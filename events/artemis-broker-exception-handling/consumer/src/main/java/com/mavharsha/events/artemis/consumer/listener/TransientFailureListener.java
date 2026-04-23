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
@Requires(property = "scenarios.transient-failure.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class TransientFailureListener {

    private static final Logger LOG = LoggerFactory.getLogger(TransientFailureListener.class);
    private static final int FAIL_TIMES = 2;

    private final FailureCounter counter;

    public TransientFailureListener(FailureCounter counter) {
        this.counter = counter;
    }

    @Queue(value = "scenarios.transient-failure", transacted = true)
    void onMessage(@MessageBody ScenarioMessage message) {
        int attempt = counter.recordAttempt(message.messageId());
        LOG.info("transient-failure attempt={} id={}", attempt, message.messageId());
        if (attempt <= FAIL_TIMES) {
            throw new RuntimeException("simulated transient failure attempt=" + attempt);
        }
        counter.recordSuccess(message.messageId());
        LOG.info("transient-failure succeeded id={} totalAttempts={}", message.messageId(), attempt);
    }

    public int attemptsFor(String messageId) {
        return counter.attemptsFor(messageId);
    }

    public int successesFor(String messageId) {
        return counter.successesFor(messageId);
    }
}
