package com.example.ecommerce.security.jwt;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Login response DTO containing JWT tokens.
 */
@Serdeable
public record AuthResponse(
        String accessToken,
        String tokenType,
        Long expiresIn,
        String username
) {
    public static AuthResponse of(String accessToken, String username, long expiresInSeconds) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, username);
    }
}
