package com.mavharsha.events.artemis.consumer;

import io.micronaut.runtime.Micronaut;

public final class ArtemisConsumerApplication {

    private ArtemisConsumerApplication() { }

    public static void main(String[] args) {
        Micronaut.run(ArtemisConsumerApplication.class, args);
    }
}
