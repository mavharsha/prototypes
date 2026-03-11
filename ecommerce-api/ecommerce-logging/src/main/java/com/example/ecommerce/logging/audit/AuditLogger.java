package com.example.ecommerce.logging.audit;

import com.example.ecommerce.logging.config.LoggingConfig;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Service for logging audit events.
 * Currently logs to SLF4J, but can be extended to store in database,
 * send to message queue, etc.
 */
@Singleton
public class AuditLogger {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AUDIT");

    private final LoggingConfig.AuditConfig config;

    public AuditLogger(LoggingConfig config) {
        this.config = config.getAudit();
    }

    /**
     * Log an audit event.
     */
    public void log(AuditEvent event) {
        if (!config.isEnabled()) {
            return;
        }

        AUDIT_LOG.info("AUDIT eventId={} type={} user={} action={} resource={}:{} success={} ip={} details={}",
                event.eventId(),
                event.eventType(),
                event.userId(),
                event.action(),
                event.resourceType(),
                event.resourceId(),
                event.success(),
                event.ipAddress(),
                event.details()
        );
    }

    /**
     * Convenience method for logging a successful action.
     */
    public void logSuccess(String userId, String action, String resourceType, String resourceId) {
        log(AuditEvent.builder()
                .eventType("ACTION")
                .userId(userId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .success(true)
                .build());
    }

    /**
     * Convenience method for logging a failed action.
     */
    public void logFailure(String userId, String action, String resourceType, String resourceId, String reason) {
        log(AuditEvent.builder()
                .eventType("ACTION")
                .userId(userId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .success(false)
                .details(Map.of("reason", reason))
                .build());
    }

    /**
     * Log a security event (login, logout, etc).
     */
    public void logSecurityEvent(String eventType, String userId, boolean success, String ipAddress, Map<String, Object> details) {
        log(AuditEvent.builder()
                .eventType(eventType)
                .userId(userId)
                .action(eventType)
                .ipAddress(ipAddress)
                .success(success)
                .details(details)
                .build());
    }

    /**
     * Log a login attempt.
     */
    public void logLogin(String userId, boolean success, String ipAddress) {
        logSecurityEvent("LOGIN", userId, success, ipAddress, null);
    }

    /**
     * Log a logout.
     */
    public void logLogout(String userId, String ipAddress) {
        logSecurityEvent("LOGOUT", userId, true, ipAddress, null);
    }

    /**
     * Log data access.
     */
    public void logDataAccess(String userId, String resourceType, String resourceId, String operation) {
        log(AuditEvent.builder()
                .eventType("DATA_ACCESS")
                .userId(userId)
                .action(operation)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .success(true)
                .build());
    }
}
