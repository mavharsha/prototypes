package com.mavharsha.events.artemis.consumer.listener;

import com.mavharsha.events.artemis.consumer.model.ScenarioMessage;
import io.micronaut.context.annotation.Requires;
import io.micronaut.jms.annotations.JMSListener;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;
import io.micronaut.retry.annotation.Retryable;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@Singleton
@Requires(property = "scenarios.app-retry.enabled", value = "true", defaultValue = "true")
@JMSListener(CONNECTION_FACTORY_BEAN_NAME)
public class AppRetryListener {

    private static final Logger LOG = LoggerFactory.getLogger(AppRetryListener.class);

    private final AtomicInteger handlerCalls = new AtomicInteger();
    private final AtomicInteger innerCalls = new AtomicInteger();
    private final AtomicBoolean succeeded = new AtomicBoolean();

    @Queue("scenarios.app-retry")
    void onMessage(@MessageBody ScenarioMessage message) {
        handlerCalls.incrementAndGet();
        doWork(message);
        succeeded.set(true);
        LOG.info("app-retry succeeded id={} afterInner={}", message.messageId(), innerCalls.get());
    }

    @Retryable(attempts = "3", delay = "200ms", multiplier = "2.0")
    protected void doWork(ScenarioMessage message) {
        int n = innerCalls.incrementAndGet();
        LOG.info("app-retry innerCall={} id={}", n, message.messageId());
        if (n < 3) {
            throw new RuntimeException("simulated transient inner failure n=" + n);
        }
    }

    public int handlerInvocations() { return handlerCalls.get(); }
    public int innerInvocations() { return innerCalls.get(); }
    public boolean succeeded() { return succeeded.get(); }
}
