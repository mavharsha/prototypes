package com.example.ecommerce.logging.filter;

import com.example.ecommerce.logging.config.LoggingConfig;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.security.authentication.Authentication;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * HTTP filter that logs all requests and responses.
 * Also sets up MDC context for correlation.
 */
@Filter("/**")
public class RequestLoggingFilter implements HttpServerFilter {

    private static final Logger LOG = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final int ORDER = -200; // Run before rate limiting

    private final LoggingConfig.RequestResponseConfig config;

    public RequestLoggingFilter(LoggingConfig config) {
        this.config = config.getRequestResponse();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        if (!config.isEnabled() || shouldExclude(request.getPath())) {
            return chain.proceed(request);
        }

        String requestId = generateRequestId();
        long startTime = System.currentTimeMillis();

        // Set up MDC for correlated logging
        MDC.put("requestId", requestId);
        MDC.put("method", request.getMethod().name());
        MDC.put("path", request.getPath());

        // Extract user info if authenticated
        Optional<Authentication> auth = request.getAttribute("micronaut.AUTHENTICATION", Authentication.class);
        auth.ifPresent(a -> MDC.put("userId", a.getName()));

        // Log request
        logRequest(request, requestId);

        return Mono.from(chain.proceed(request))
                .doOnSuccess(response -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logResponse(request, response, duration, requestId);
                })
                .doOnError(error -> {
                    long duration = System.currentTimeMillis() - startTime;
                    logError(request, error, duration, requestId);
                })
                .doFinally(signal -> {
                    // Clean up MDC
                    MDC.clear();
                });
    }

    private boolean shouldExclude(String path) {
        return config.getExcludePaths().stream()
                .anyMatch(path::startsWith);
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void logRequest(HttpRequest<?> request, String requestId) {
        StringBuilder sb = new StringBuilder();
        sb.append(">>> REQUEST [").append(requestId).append("] ");
        sb.append(request.getMethod()).append(" ").append(request.getPath());

        if (request.getUri().getQuery() != null) {
            sb.append("?").append(request.getUri().getQuery());
        }

        if (config.isLogHeaders()) {
            Map<String, String> headers = new HashMap<>();
            request.getHeaders().forEach((name, values) -> {
                String value = isSensitiveHeader(name) ? "[REDACTED]" : String.join(", ", values);
                headers.put(name, value);
            });
            sb.append(" headers=").append(headers);
        }

        LOG.info(sb.toString());
    }

    private void logResponse(HttpRequest<?> request, MutableHttpResponse<?> response, long duration, String requestId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<<< RESPONSE [").append(requestId).append("] ");
        sb.append(request.getMethod()).append(" ").append(request.getPath());
        sb.append(" status=").append(response.getStatus().getCode());
        sb.append(" duration=").append(duration).append("ms");

        if (response.getStatus().getCode() >= 400) {
            LOG.warn(sb.toString());
        } else {
            LOG.info(sb.toString());
        }
    }

    private void logError(HttpRequest<?> request, Throwable error, long duration, String requestId) {
        LOG.error("<<< ERROR [{}] {} {} error={} duration={}ms",
                requestId,
                request.getMethod(),
                request.getPath(),
                error.getMessage(),
                duration);
    }

    private boolean isSensitiveHeader(String headerName) {
        return config.getSensitiveHeaders().stream()
                .anyMatch(h -> h.equalsIgnoreCase(headerName));
    }
}
