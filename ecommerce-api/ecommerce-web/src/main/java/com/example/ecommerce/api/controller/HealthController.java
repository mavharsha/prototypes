package com.example.ecommerce.api.controller;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;

import java.time.Instant;
import java.util.Map;

@Controller("/health")
@Secured(SecurityRule.IS_ANONYMOUS)
public class HealthController {

    @Get
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString()
        );
    }
}
