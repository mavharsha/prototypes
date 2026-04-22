package com.mavharsha.events.artemis.producer;

import io.micronaut.runtime.Micronaut;

public final class ArtemisProducerApplication {

    private ArtemisProducerApplication() { }

    public static void main(String[] args) {
        Micronaut.run(ArtemisProducerApplication.class, args);
    }
}
