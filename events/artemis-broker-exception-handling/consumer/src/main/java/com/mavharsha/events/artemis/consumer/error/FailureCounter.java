package com.mavharsha.events.artemis.consumer.error;

import jakarta.inject.Singleton;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class FailureCounter {

    private final ConcurrentMap<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> successes = new ConcurrentHashMap<>();

    public int recordAttempt(String messageId) {
        return attempts.computeIfAbsent(messageId, k -> new AtomicInteger()).incrementAndGet();
    }

    public int recordSuccess(String messageId) {
        return successes.computeIfAbsent(messageId, k -> new AtomicInteger()).incrementAndGet();
    }

    public int attemptsFor(String messageId) {
        AtomicInteger v = attempts.get(messageId);
        return v == null ? 0 : v.get();
    }

    public int successesFor(String messageId) {
        AtomicInteger v = successes.get(messageId);
        return v == null ? 0 : v.get();
    }
}
