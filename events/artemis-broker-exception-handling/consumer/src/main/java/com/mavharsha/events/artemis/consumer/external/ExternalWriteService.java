package com.mavharsha.events.artemis.consumer.external;

import jakarta.inject.Singleton;

import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class ExternalWriteService {

    private final AtomicInteger failuresRemaining = new AtomicInteger();
    private final AtomicInteger committed = new AtomicInteger();

    public void failNextN(int n) {
        failuresRemaining.set(n);
    }

    public void write(String id, String payload) {
        if (failuresRemaining.getAndDecrement() > 0) {
            throw new RuntimeException("external write failed for id=" + id);
        }
        committed.incrementAndGet();
    }

    public int committedCount() { return committed.get(); }
}
