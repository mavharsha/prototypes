package com.example.ecommerce.security.jwt;

import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory user store for demo purposes.
 * Replace with database-backed implementation in production.
 */
@Singleton
public class InMemoryUserRepository {

    public record User(String id, String username, String password, List<String> roles) {}

    private final Map<String, User> usersByUsername = new ConcurrentHashMap<>();

    public InMemoryUserRepository() {
        // Seed demo users
        addUser("admin", "admin123", List.of("ADMIN", "USER"));
        addUser("user", "user123", List.of("USER"));
        addUser("guest", "guest123", List.of("GUEST"));
    }

    private void addUser(String username, String password, List<String> roles) {
        String id = UUID.randomUUID().toString();
        usersByUsername.put(username, new User(id, username, password, roles));
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(usersByUsername.get(username));
    }

    public boolean validateCredentials(String username, String password) {
        return findByUsername(username)
                .map(user -> user.password().equals(password))
                .orElse(false);
    }
}
