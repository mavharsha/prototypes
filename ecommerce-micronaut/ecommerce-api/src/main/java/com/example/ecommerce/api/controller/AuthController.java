package com.example.ecommerce.api.controller;

import com.example.ecommerce.logging.audit.AuditLogger;
import com.example.ecommerce.security.jwt.AuthRequest;
import com.example.ecommerce.security.jwt.AuthResponse;
import com.example.ecommerce.security.jwt.InMemoryUserRepository;
import com.example.ecommerce.security.jwt.InMemoryUserRepository.User;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import io.micronaut.security.token.jwt.generator.JwtTokenGenerator;

import java.util.Map;
import java.util.Optional;

/**
 * Authentication controller for login/logout.
 */
@Controller("/api/auth")
@Secured(SecurityRule.IS_ANONYMOUS)
public class AuthController {

    private final InMemoryUserRepository userRepository;
    private final JwtTokenGenerator tokenGenerator;
    private final AuditLogger auditLogger;

    public AuthController(
            InMemoryUserRepository userRepository,
            JwtTokenGenerator tokenGenerator,
            AuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.tokenGenerator = tokenGenerator;
        this.auditLogger = auditLogger;
    }

    @Post("/login")
    public HttpResponse<?> login(@Body AuthRequest request, HttpRequest<?> httpRequest) {
        String ipAddress = extractIpAddress(httpRequest);

        Optional<User> userOpt = userRepository.findByUsername(request.username());

        if (userOpt.isEmpty() || !userRepository.validateCredentials(request.username(), request.password())) {
            auditLogger.logLogin(request.username(), false, ipAddress);
            return HttpResponse.unauthorized()
                    .body(Map.of("error", "UNAUTHORIZED", "message", "Invalid credentials"));
        }

        User user = userOpt.get();

        // Generate JWT token
        Map<String, Object> claims = Map.of(
                "sub", user.id(),
                "username", user.username(),
                "roles", user.roles()
        );

        Optional<String> tokenOpt = tokenGenerator.generateToken(claims);

        if (tokenOpt.isEmpty()) {
            return HttpResponse.serverError()
                    .body(Map.of("error", "TOKEN_GENERATION_FAILED", "message", "Failed to generate token"));
        }

        auditLogger.logLogin(user.id(), true, ipAddress);

        return HttpResponse.ok(AuthResponse.of(tokenOpt.get(), user.username(), 3600));
    }

    @Post("/logout")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<?> logout(Authentication authentication, HttpRequest<?> httpRequest) {
        String ipAddress = extractIpAddress(httpRequest);
        auditLogger.logLogout(authentication.getName(), ipAddress);

        // JWT is stateless, so we just return success
        // In production, you might add the token to a blacklist
        return HttpResponse.ok(Map.of("message", "Logged out successfully"));
    }

    @Post("/refresh")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<?> refresh(Authentication authentication) {
        // Generate new token with same claims
        Map<String, Object> claims = Map.of(
                "sub", authentication.getName(),
                "username", authentication.getAttributes().get("username"),
                "roles", authentication.getRoles()
        );

        Optional<String> tokenOpt = tokenGenerator.generateToken(claims);

        if (tokenOpt.isEmpty()) {
            return HttpResponse.serverError()
                    .body(Map.of("error", "TOKEN_GENERATION_FAILED", "message", "Failed to generate token"));
        }

        String username = (String) authentication.getAttributes().getOrDefault("username", "unknown");
        return HttpResponse.ok(AuthResponse.of(tokenOpt.get(), username, 3600));
    }

    private String extractIpAddress(HttpRequest<?> request) {
        String forwardedFor = request.getHeaders().get("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        try {
            java.net.InetSocketAddress remoteAddr = request.getRemoteAddress();
            if (remoteAddr != null && remoteAddr.getAddress() != null) {
                return remoteAddr.getAddress().getHostAddress();
            }
        } catch (Exception e) {
            // Ignore
        }
        return "unknown";
    }
}
