package com.example.ecommerce.security.jwt;

import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.provider.HttpRequestAuthenticationProvider;
import jakarta.inject.Singleton;

/**
 * Authenticates users against the in-memory user repository.
 */
@Singleton
public class CustomAuthenticationProvider<B> implements HttpRequestAuthenticationProvider<B> {

    private final InMemoryUserRepository userRepository;

    public CustomAuthenticationProvider(InMemoryUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AuthenticationResponse authenticate(
            HttpRequest<B> httpRequest,
            AuthenticationRequest<String, String> authenticationRequest) {

        String username = authenticationRequest.getIdentity();
        String password = authenticationRequest.getSecret();

        return userRepository.findByUsername(username)
                .filter(user -> user.password().equals(password))
                .map(user -> AuthenticationResponse.success(
                        user.id(),
                        user.roles(),
                        java.util.Map.of("username", user.username())
                ))
                .orElse(AuthenticationResponse.failure("Invalid credentials"));
    }
}
