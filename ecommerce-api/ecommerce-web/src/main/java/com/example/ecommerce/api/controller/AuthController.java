package com.example.ecommerce.api.controller;

import com.example.ecommerce.logging.audit.AuditLogger;
import com.example.ecommerce.security.jwt.AuthRequest;
import com.example.ecommerce.security.jwt.AuthResponse;
import com.example.ecommerce.security.jwt.InMemoryUserRepository;
import com.example.ecommerce.security.jwt.JwtService;
import com.example.ecommerce.security.config.SecurityConfig;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;

import jakarta.validation.Valid;
import java.util.Map;

@Controller("/api/auth")
public class AuthController {

    private final InMemoryUserRepository userRepository;
    private final JwtService jwtService;
    private final SecurityConfig securityConfig;
    private final AuditLogger auditLogger;

    public AuthController(InMemoryUserRepository userRepository, JwtService jwtService,
                           SecurityConfig securityConfig, AuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.securityConfig = securityConfig;
        this.auditLogger = auditLogger;
    }

    @Post("/login")
    @Secured(SecurityRule.IS_ANONYMOUS)
    public HttpResponse<?> login(@Body @Valid AuthRequest request, HttpRequest<?> httpRequest) {
        String ipAddress = extractIpAddress(httpRequest);

        return userRepository.findByUsername(request.username())
                .filter(user -> user.password().equals(request.password()))
                .map(user -> {
                    String token = jwtService.generateToken(user.id(), user.username(), user.roles())
                            .orElseThrow(() -> new RuntimeException("Failed to generate token"));
                    auditLogger.logLogin(user.username(), true, ipAddress);
                    return HttpResponse.ok(AuthResponse.of(token, user.username(),
                            securityConfig.getJwt().getExpirationSeconds()));
                })
                .orElseGet(() -> {
                    auditLogger.logLogin(request.username(), false, ipAddress);
                    return HttpResponse.unauthorized();
                });
    }

    @Post("/logout")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<?> logout(Authentication authentication, HttpRequest<?> httpRequest) {
        auditLogger.logLogout(authentication.getName(), extractIpAddress(httpRequest));
        return HttpResponse.ok(Map.of("message", "Logged out successfully"));
    }

    @Post("/refresh")
    @Secured(SecurityRule.IS_AUTHENTICATED)
    public HttpResponse<?> refresh(Authentication authentication) {
        String userId = authentication.getName();
        return userRepository.findByUsername(
                        authentication.getAttributes().getOrDefault("username", userId).toString())
                .map(user -> {
                    String token = jwtService.generateToken(user.id(), user.username(), user.roles())
                            .orElseThrow(() -> new RuntimeException("Failed to generate token"));
                    return HttpResponse.ok(AuthResponse.of(token, user.username(),
                            securityConfig.getJwt().getExpirationSeconds()));
                })
                .orElse(HttpResponse.unauthorized());
    }

    private String extractIpAddress(HttpRequest<?> request) {
        String forwarded = request.getHeaders().get("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        try {
            return request.getRemoteAddress().getAddress().getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
