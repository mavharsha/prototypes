package com.example.ecommerce.security.jwt;

import io.micronaut.security.token.jwt.generator.JwtTokenGenerator;
import io.micronaut.security.authentication.Authentication;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;

/**
 * Service for generating JWT tokens.
 */
@Singleton
public class JwtService {

    private final JwtTokenGenerator tokenGenerator;

    public JwtService(JwtTokenGenerator tokenGenerator) {
        this.tokenGenerator = tokenGenerator;
    }

    /**
     * Generates a JWT token for the given user.
     */
    public Optional<String> generateToken(String userId, String username, java.util.List<String> roles) {
        Map<String, Object> claims = Map.of(
                "sub", userId,
                "username", username,
                "roles", roles
        );
        return tokenGenerator.generateToken(claims);
    }

    /**
     * Extracts user ID from authentication.
     */
    public static String getUserId(Authentication authentication) {
        return authentication.getName();
    }
}
