package com.mavharsha.events.artemis.consumer.listener;

import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.jms.annotations.Message;
import io.micronaut.messaging.annotation.MessageBody;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.dlq-observer.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class DeadLetterObserver {

    private static final Logger LOG = LoggerFactory.getLogger(DeadLetterObserver.class);

    private final AtomicLong count = new AtomicLong();

    @Queue("DLQ")
    void onDeadLetter(@MessageBody String body, @Message jakarta.jms.Message raw) {
        long n = count.incrementAndGet();
        String origAddress = null;
        String origQueue = null;
        try {
            origAddress = raw.getStringProperty("_AMQ_ORIG_ADDRESS");
            origQueue = raw.getStringProperty("_AMQ_ORIG_QUEUE");
        } catch (Exception e) {
            LOG.debug("Could not read origin headers from DLQ message #{}: {}", n, e.getMessage());
        }
        LOG.warn("DLQ arrival #{} origAddress={} origQueue={} bodyPreview={}",
                n, origAddress, origQueue,
                body.length() > 120 ? body.substring(0, 120) + "…" : body);
    }

    public long dlqCount() {
        return count.get();
    }
}
