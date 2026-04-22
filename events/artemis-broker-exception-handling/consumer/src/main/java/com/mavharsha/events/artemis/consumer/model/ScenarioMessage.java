package com.mavharsha.events.artemis.consumer.model;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;

@Serdeable
public record ScenarioMessage(String messageId,
                              Scenario scenario,
                              String payload,
                              Instant emittedAt) { }
