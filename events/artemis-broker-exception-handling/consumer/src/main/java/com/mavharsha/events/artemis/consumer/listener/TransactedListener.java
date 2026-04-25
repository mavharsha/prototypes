package com.mavharsha.events.artemis.consumer.listener;

import com.mavharsha.events.artemis.consumer.external.ExternalWriteService;
import com.mavharsha.events.artemis.consumer.model.ScenarioMessage;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.transacted.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class TransactedListener {

    private static final Logger LOG = LoggerFactory.getLogger(TransactedListener.class);

    private final ExternalWriteService external;
    private final AtomicInteger attempts = new AtomicInteger();

    public TransactedListener(ExternalWriteService external) {
        this.external = external;
    }

    @Queue(value = "scenarios.transacted", transacted = true)
    void onMessage(@MessageBody ScenarioMessage message) {
        int n = attempts.incrementAndGet();
        LOG.info("transacted attempt={} id={}", n, message.messageId());
        external.write(message.messageId(), message.payload());
        LOG.info("transacted committed id={}", message.messageId());
    }

    public int attempts() { return attempts.get(); }
}
