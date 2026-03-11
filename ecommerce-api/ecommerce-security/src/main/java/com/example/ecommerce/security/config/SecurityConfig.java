package com.example.ecommerce.security.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Introspected;

import java.util.List;

/**
 * Security configuration properties.
 * Configure via application.yml:
 *
 * security:
 *   jwt:
 *     expiration-seconds: 3600
 *   public-paths:
 *     - /api/auth/**
 *     - /health
 */
@ConfigurationProperties("security")
@Introspected
public class SecurityConfig {

    private JwtConfig jwt = new JwtConfig();
    private List<String> publicPaths = List.of("/api/auth/**", "/health");

    public JwtConfig getJwt() {
        return jwt;
    }

    public void setJwt(JwtConfig jwt) {
        this.jwt = jwt;
    }

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    @Introspected
    public static class JwtConfig {
        private long expirationSeconds = 3600; // 1 hour

        public long getExpirationSeconds() {
            return expirationSeconds;
        }

        public void setExpirationSeconds(long expirationSeconds) {
            this.expirationSeconds = expirationSeconds;
        }
    }
}
