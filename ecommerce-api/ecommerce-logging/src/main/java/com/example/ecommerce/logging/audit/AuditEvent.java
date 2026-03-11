package com.example.ecommerce.logging.audit;

import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Map;

/**
 * Represents an auditable event in the system.
 */
@Serdeable
public record AuditEvent(
        String eventId,
        String eventType,
        String userId,
        String action,
        String resourceType,
        String resourceId,
        Map<String, Object> details,
        String ipAddress,
        Instant timestamp,
        boolean success
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String eventId;
        private String eventType;
        private String userId;
        private String action;
        private String resourceType;
        private String resourceId;
        private Map<String, Object> details;
        private String ipAddress;
        private Instant timestamp;
        private boolean success = true;

        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder resourceType(String resourceType) {
            this.resourceType = resourceType;
            return this;
        }

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public AuditEvent build() {
            if (eventId == null) {
                eventId = java.util.UUID.randomUUID().toString();
            }
            if (timestamp == null) {
                timestamp = Instant.now();
            }
            return new AuditEvent(
                    eventId, eventType, userId, action,
                    resourceType, resourceId, details,
                    ipAddress, timestamp, success
            );
        }
    }
}
