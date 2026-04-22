package com.mavharsha.events.artemis.producer.model;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.UUID;

@Serdeable
public record ScenarioMessage(String messageId,
                              Scenario scenario,
                              String payload,
                              Instant emittedAt) {

    public static ScenarioMessage of(Scenario scenario, String payload) {
        return new ScenarioMessage(UUID.randomUUID().toString(), scenario, payload, Instant.now());
    }
}
