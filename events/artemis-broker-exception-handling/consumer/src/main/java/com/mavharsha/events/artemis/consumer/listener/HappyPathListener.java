package com.mavharsha.events.artemis.consumer.listener;

import com.mavharsha.events.artemis.consumer.model.ScenarioMessage;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.happy-path.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class HappyPathListener {

    private static final Logger LOG = LoggerFactory.getLogger(HappyPathListener.class);
    private final AtomicLong processed = new AtomicLong();

    @Queue("scenarios.happy-path")
    void onMessage(@MessageBody ScenarioMessage message) {
        long n = processed.incrementAndGet();
        if (n % 100 == 0) {
            LOG.info("happy-path processed={} lastId={}", n, message.messageId());
        }
    }

    public long processedCount() {
        return processed.get();
    }
}
