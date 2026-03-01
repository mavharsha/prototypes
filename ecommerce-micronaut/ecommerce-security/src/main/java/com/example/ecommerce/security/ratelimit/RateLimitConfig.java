package com.example.ecommerce.security.ratelimit;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Introspected;

/**
 * Configuration for rate limiting.
 * Configure via application.yml:
 *
 * rate-limit:
 *   enabled: true
 *   requests-per-minute: 60
 *   burst-capacity: 10
 */
@ConfigurationProperties("rate-limit")
@Introspected
public class RateLimitConfig {

    private boolean enabled = true;
    private int requestsPerMinute = 60;
    private int burstCapacity = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRequestsPerMinute() {
        return requestsPerMinute;
    }

    public void setRequestsPerMinute(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    public int getBurstCapacity() {
        return burstCapacity;
    }

    public void setBurstCapacity(int burstCapacity) {
        this.burstCapacity = burstCapacity;
    }
}
