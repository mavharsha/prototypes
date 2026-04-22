package com.mavharsha.events.artemis.producer.model;

import java.util.Map;

public final class ScenarioQueues {

    public static final String HAPPY_PATH = "scenarios.happy-path";
    public static final String TRANSIENT_FAILURE = "scenarios.transient-failure";
    public static final String PERMANENT_FAILURE = "scenarios.permanent-failure";
    public static final String POISON_PILL = "scenarios.poison-pill";
    public static final String EXPIRED_TTL = "scenarios.expired-ttl";
    public static final String BUSINESS_VALIDATION = "scenarios.business-validation";
    public static final String BUSINESS_DLQ = "scenarios.business-dlq";
    public static final String APP_RETRY = "scenarios.app-retry";
    public static final String TRANSACTED = "scenarios.transacted";
    public static final String IDEMPOTENT = "scenarios.idempotent";
    public static final String SLOW_CONSUMER = "scenarios.slow-consumer";
    public static final String BROKER_DLQ = "DLQ";
    public static final String BROKER_EXPIRY = "ExpiryQueue";

    private static final Map<Scenario, String> BY_SCENARIO = Map.of(
            Scenario.HAPPY_PATH, HAPPY_PATH,
            Scenario.TRANSIENT_FAILURE, TRANSIENT_FAILURE,
            Scenario.PERMANENT_FAILURE, PERMANENT_FAILURE,
            Scenario.POISON_PILL, POISON_PILL,
            Scenario.EXPIRED_TTL, EXPIRED_TTL,
            Scenario.BUSINESS_VALIDATION, BUSINESS_VALIDATION,
            Scenario.APP_RETRY, APP_RETRY,
            Scenario.TRANSACTED, TRANSACTED,
            Scenario.IDEMPOTENT, IDEMPOTENT,
            Scenario.SLOW_CONSUMER, SLOW_CONSUMER
    );

    private ScenarioQueues() { }

    public static String queueFor(Scenario s) {
        return BY_SCENARIO.get(s);
    }
}
