package com.example.ecommerce.security.jwt;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * Represents the authenticated user principal.
 * Extracted from JWT claims.
 */
@Serdeable
public record UserPrincipal(
        String userId,
        String username,
        List<String> roles
) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }
}
