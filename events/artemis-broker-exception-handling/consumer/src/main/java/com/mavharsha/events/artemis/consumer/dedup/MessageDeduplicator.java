package com.mavharsha.events.artemis.consumer.dedup;

import jakarta.inject.Singleton;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class MessageDeduplicator {

    private final Set<String> seen = ConcurrentHashMap.newKeySet();

    public boolean claim(String messageId) {
        return seen.add(messageId);
    }
}
