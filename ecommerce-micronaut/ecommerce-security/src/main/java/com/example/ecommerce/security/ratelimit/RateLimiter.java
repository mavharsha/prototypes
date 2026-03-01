package com.example.ecommerce.security.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple rate limiter using Caffeine cache.
 * Each client (identified by IP or user ID) gets their own token bucket.
 */
@Singleton
public class RateLimiter {

    private final RateLimitConfig config;
    private final Cache<String, TokenBucket> bucketCache;

    public RateLimiter(RateLimitConfig config) {
        this.config = config;
        this.bucketCache = Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .maximumSize(100_000)
                .build();
    }

    /**
     * Attempts to consume a token for the given client.
     * @param clientId The client identifier (IP address or user ID)
     * @return RateLimitResult indicating if request is allowed
     */
    public RateLimitResult tryConsume(String clientId) {
        if (!config.isEnabled()) {
            return RateLimitResult.allow();
        }

        TokenBucket bucket = bucketCache.get(clientId, k -> new TokenBucket(
                config.getRequestsPerMinute() + config.getBurstCapacity(),
                config.getRequestsPerMinute(),
                Duration.ofMinutes(1)
        ));

        if (bucket.tryConsume()) {
            return RateLimitResult.allow(bucket.getAvailableTokens(), config.getRequestsPerMinute());
        } else {
            long waitTime = bucket.getWaitTimeMs();
            return RateLimitResult.deny(waitTime, config.getRequestsPerMinute());
        }
    }

    /**
     * Simple token bucket implementation.
     */
    private static class TokenBucket {
        private final int maxTokens;
        private final int refillRate;
        private final long refillPeriodMs;
        private final AtomicInteger tokens;
        private final AtomicLong lastRefillTime;

        TokenBucket(int maxTokens, int refillRate, Duration refillPeriod) {
            this.maxTokens = maxTokens;
            this.refillRate = refillRate;
            this.refillPeriodMs = refillPeriod.toMillis();
            this.tokens = new AtomicInteger(maxTokens);
            this.lastRefillTime = new AtomicLong(System.currentTimeMillis());
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }

        int getAvailableTokens() {
            refill();
            return tokens.get();
        }

        long getWaitTimeMs() {
            if (tokens.get() > 0) return 0;
            long timeSinceLastRefill = System.currentTimeMillis() - lastRefillTime.get();
            long timePerToken = refillPeriodMs / refillRate;
            return Math.max(0, timePerToken - timeSinceLastRefill);
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long timePassed = now - lastRefillTime.get();
            int tokensToAdd = (int) (timePassed * refillRate / refillPeriodMs);
            if (tokensToAdd > 0) {
                int newTokens = Math.min(maxTokens, tokens.get() + tokensToAdd);
                tokens.set(newTokens);
                lastRefillTime.set(now);
            }
        }
    }

    /**
     * Result of a rate limit check.
     */
    public record RateLimitResult(
            boolean isAllowed,
            long remainingTokens,
            long retryAfterMs,
            int limit
    ) {
        public static RateLimitResult allow() {
            return new RateLimitResult(true, -1, 0, -1);
        }

        public static RateLimitResult allow(long remaining, int limit) {
            return new RateLimitResult(true, remaining, 0, limit);
        }

        public static RateLimitResult deny(long retryAfterMs, int limit) {
            return new RateLimitResult(false, 0, retryAfterMs, limit);
        }
    }
}
