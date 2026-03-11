package com.example.ecommerce.api.handler;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ErrorResponse(String error, String message) {
}
