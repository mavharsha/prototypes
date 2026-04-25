package com.mavharsha.events.artemis.producer.publish;

import com.mavharsha.events.artemis.producer.model.ScenarioMessage;
import io.micronaut.jms.annotations.JMSProducer;
import io.micronaut.jms.annotations.Queue;
import io.micronaut.messaging.annotation.MessageBody;

import static io.micronaut.jms.activemq.artemis.configuration.ActiveMqArtemisConfiguration.CONNECTION_FACTORY_BEAN_NAME;

@JMSProducer(CONNECTION_FACTORY_BEAN_NAME)
public interface ScenarioPublisher {

    @Queue("scenarios.happy-path")
    void sendHappyPath(@MessageBody ScenarioMessage message);

    @Queue("scenarios.transient-failure")
    void sendTransientFailure(@MessageBody ScenarioMessage message);

    @Queue("scenarios.permanent-failure")
    void sendPermanentFailure(@MessageBody ScenarioMessage message);

    @Queue("scenarios.poison-pill")
    void sendPoisonPill(@MessageBody String rawBody);

    @Queue("scenarios.business-validation")
    void sendBusinessValidation(@MessageBody ScenarioMessage message);

    @Queue("scenarios.app-retry")
    void sendAppRetry(@MessageBody ScenarioMessage message);

    @Queue("scenarios.transacted")
    void sendTransacted(@MessageBody ScenarioMessage message);

    @Queue("scenarios.idempotent")
    void sendIdempotent(@MessageBody ScenarioMessage message);

    @Queue("scenarios.slow-consumer")
    void sendSlowConsumer(@MessageBody ScenarioMessage message);
}
