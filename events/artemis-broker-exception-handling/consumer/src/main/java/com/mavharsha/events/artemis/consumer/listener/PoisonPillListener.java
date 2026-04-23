package com.mavharsha.events.artemis.consumer.listener;

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
@Requires(property = "scenarios.poison-pill.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class PoisonPillListener {

    private static final Logger LOG = LoggerFactory.getLogger(PoisonPillListener.class);

    @Queue(value = "scenarios.poison-pill", transacted = true)
    void onMessage(@MessageBody ScenarioMessage message) {
        LOG.info("poison-pill accepted (shouldn't usually reach here) id={}", message.messageId());
    }
}
