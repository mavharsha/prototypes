package com.example.ecommerce.security.filter;

import com.example.ecommerce.security.ratelimit.RateLimitConfig;
import com.example.ecommerce.security.ratelimit.RateLimiter;
import com.example.ecommerce.security.ratelimit.RateLimiter.RateLimitResult;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.security.authentication.Authentication;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

/**
 * HTTP filter that applies rate limiting to all requests.
 * Uses IP address for unauthenticated requests, user ID for authenticated.
 */
@Filter("/**")
public class RateLimitFilter implements HttpServerFilter {

    private static final int ORDER = -100; // Run early in filter chain

    private final RateLimiter rateLimiter;
    private final RateLimitConfig config;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitConfig config) {
        this.rateLimiter = rateLimiter;
        this.config = config;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
        if (!config.isEnabled()) {
            return chain.proceed(request);
        }

        String clientId = extractClientId(request);
        RateLimitResult result = rateLimiter.tryConsume(clientId);

        if (result.isAllowed()) {
            return Mono.from(chain.proceed(request))
                    .map(response -> addRateLimitHeaders(response, result));
        } else {
            return Mono.just(createRateLimitedResponse(result));
        }
    }

    private String extractClientId(HttpRequest<?> request) {
        // Try to get authenticated user ID first
        Optional<Authentication> auth = request.getAttribute("micronaut.AUTHENTICATION", Authentication.class);
        if (auth.isPresent()) {
            return "user:" + auth.get().getName();
        }

        // Fall back to IP address
        String forwardedFor = request.getHeaders().get("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }

        try {
            java.net.InetSocketAddress remoteAddr = request.getRemoteAddress();
            if (remoteAddr != null && remoteAddr.getAddress() != null) {
                return "ip:" + remoteAddr.getAddress().getHostAddress();
            }
        } catch (Exception e) {
            // Ignore
        }
        return "ip:unknown";
    }

    private MutableHttpResponse<?> addRateLimitHeaders(MutableHttpResponse<?> response, RateLimitResult result) {
        response.header("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.header("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));
        return response;
    }

    private MutableHttpResponse<?> createRateLimitedResponse(RateLimitResult result) {
        Map<String, Object> body = Map.of(
                "error", "RATE_LIMITED",
                "message", "Too many requests. Please try again later.",
                "retryAfterMs", result.retryAfterMs()
        );

        return HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(body)
                .header("X-RateLimit-Limit", String.valueOf(result.limit()))
                .header("X-RateLimit-Remaining", "0")
                .header("Retry-After", String.valueOf(result.retryAfterMs() / 1000 + 1));
    }
}
