package com.mavharsha.events.artemis.producer.model;

public enum Scenario {
    HAPPY_PATH,
    TRANSIENT_FAILURE,
    PERMANENT_FAILURE,
    POISON_PILL,
    EXPIRED_TTL,
    BUSINESS_VALIDATION,
    APP_RETRY,
    TRANSACTED,
    IDEMPOTENT,
    SLOW_CONSUMER
}
