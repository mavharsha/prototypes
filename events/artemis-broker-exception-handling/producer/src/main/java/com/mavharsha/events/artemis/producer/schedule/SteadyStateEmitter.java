package com.mavharsha.events.artemis.producer.schedule;

import com.mavharsha.events.artemis.producer.model.Scenario;
import com.mavharsha.events.artemis.producer.model.ScenarioMessage;
import com.mavharsha.events.artemis.producer.publish.ScenarioPublisher;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
@Requires(property = "emitter.enabled", value = "true", defaultValue = "true")
public class SteadyStateEmitter {

    private static final Logger LOG = LoggerFactory.getLogger(SteadyStateEmitter.class);

    private final ScenarioPublisher publisher;
    private final double failureInjectionRatio;
    private final AtomicLong counter = new AtomicLong();

    public SteadyStateEmitter(ScenarioPublisher publisher,
                              @Value("${emitter.failure-injection-ratio:0.0}") double failureInjectionRatio) {
        this.publisher = publisher;
        this.failureInjectionRatio = failureInjectionRatio;
    }

    @Scheduled(fixedRate = "20ms")
    void emit() {
        long n = counter.incrementAndGet();
        boolean inject = ThreadLocalRandom.current().nextDouble() < failureInjectionRatio;
        if (inject) {
            publisher.sendTransientFailure(ScenarioMessage.of(Scenario.TRANSIENT_FAILURE, "inject-" + n));
        } else {
            publisher.sendHappyPath(ScenarioMessage.of(Scenario.HAPPY_PATH, "steady-" + n));
        }
        if (n % 500 == 0) {
            LOG.info("emitter sent={} injectionRatio={}", n, failureInjectionRatio);
        }
    }
}
