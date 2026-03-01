package com.example.ecommerce.logging.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Introspected;

import java.util.List;

/**
 * Configuration for logging.
 * Configure via application.yml:
 *
 * logging:
 *   request-response:
 *     enabled: true
 *     log-headers: true
 *     log-body: true
 *     max-body-length: 1000
 *     exclude-paths:
 *       - /health
 *   audit:
 *     enabled: true
 */
@ConfigurationProperties("logging")
@Introspected
public class LoggingConfig {

    private RequestResponseConfig requestResponse = new RequestResponseConfig();
    private AuditConfig audit = new AuditConfig();

    public RequestResponseConfig getRequestResponse() {
        return requestResponse;
    }

    public void setRequestResponse(RequestResponseConfig requestResponse) {
        this.requestResponse = requestResponse;
    }

    public AuditConfig getAudit() {
        return audit;
    }

    public void setAudit(AuditConfig audit) {
        this.audit = audit;
    }

    @Introspected
    public static class RequestResponseConfig {
        private boolean enabled = true;
        private boolean logHeaders = true;
        private boolean logBody = true;
        private int maxBodyLength = 1000;
        private List<String> excludePaths = List.of("/health", "/metrics");
        private List<String> sensitiveHeaders = List.of("Authorization", "Cookie", "X-Api-Key");

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isLogHeaders() {
            return logHeaders;
        }

        public void setLogHeaders(boolean logHeaders) {
            this.logHeaders = logHeaders;
        }

        public boolean isLogBody() {
            return logBody;
        }

        public void setLogBody(boolean logBody) {
            this.logBody = logBody;
        }

        public int getMaxBodyLength() {
            return maxBodyLength;
        }

        public void setMaxBodyLength(int maxBodyLength) {
            this.maxBodyLength = maxBodyLength;
        }

        public List<String> getExcludePaths() {
            return excludePaths;
        }

        public void setExcludePaths(List<String> excludePaths) {
            this.excludePaths = excludePaths;
        }

        public List<String> getSensitiveHeaders() {
            return sensitiveHeaders;
        }

        public void setSensitiveHeaders(List<String> sensitiveHeaders) {
            this.sensitiveHeaders = sensitiveHeaders;
        }
    }

    @Introspected
    public static class AuditConfig {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
