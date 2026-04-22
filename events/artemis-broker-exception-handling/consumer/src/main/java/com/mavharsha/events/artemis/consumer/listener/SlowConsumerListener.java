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
@Requires(property = "scenarios.slow-consumer.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class SlowConsumerListener {

    private static final Logger LOG = LoggerFactory.getLogger(SlowConsumerListener.class);
    private final AtomicLong processed = new AtomicLong();

    @Queue("scenarios.slow-consumer")
    void onMessage(@MessageBody ScenarioMessage message) throws InterruptedException {
        Thread.sleep(1000); // intentionally slow
        long n = processed.incrementAndGet();
        if (n % 5 == 0) {
            LOG.info("slow-consumer processed={} lastId={}", n, message.messageId());
        }
    }

    public long processedCount() {
        return processed.get();
    }
}
