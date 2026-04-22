package com.mavharsha.events.artemis.producer.http;

import com.mavharsha.events.artemis.producer.model.Scenario;
import com.mavharsha.events.artemis.producer.model.ScenarioMessage;
import com.mavharsha.events.artemis.producer.publish.ScenarioPublisher;
import com.mavharsha.events.artemis.producer.publish.TtlPublisher;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;

import java.util.Map;

@Controller("/trigger")
public class TriggerController {

    private final ScenarioPublisher publisher;
    private final TtlPublisher ttlPublisher;

    public TriggerController(ScenarioPublisher publisher, TtlPublisher ttlPublisher) {
        this.publisher = publisher;
        this.ttlPublisher = ttlPublisher;
    }

    @Post("/{scenario}")
    public HttpResponse<Map<String, String>> trigger(@PathVariable Scenario scenario,
                                                     @Body @io.micronaut.core.annotation.Nullable String payload) throws Exception {
        String body = payload == null ? "trigger-" + scenario : payload;
        ScenarioMessage message = ScenarioMessage.of(scenario, body);
        switch (scenario) {
            case HAPPY_PATH -> publisher.sendHappyPath(message);
            case TRANSIENT_FAILURE -> publisher.sendTransientFailure(message);
            case PERMANENT_FAILURE -> publisher.sendPermanentFailure(message);
            case POISON_PILL -> publisher.sendPoisonPill("{ this is not valid json");
            case EXPIRED_TTL -> ttlPublisher.sendExpiring(message, 500L);
            case BUSINESS_VALIDATION -> publisher.sendBusinessValidation(
                    new ScenarioMessage(message.messageId(), scenario, "INVALID-DOMAIN-VALUE", message.emittedAt()));
            case APP_RETRY -> publisher.sendAppRetry(message);
            case TRANSACTED -> publisher.sendTransacted(message);
            case IDEMPOTENT -> {
                publisher.sendIdempotent(message);
                publisher.sendIdempotent(message); // same messageId twice
            }
            case SLOW_CONSUMER -> publisher.sendSlowConsumer(message);
        }
        return HttpResponse.ok(Map.of("status", "triggered", "scenario", scenario.name(),
                                       "messageId", message.messageId()));
    }
}
