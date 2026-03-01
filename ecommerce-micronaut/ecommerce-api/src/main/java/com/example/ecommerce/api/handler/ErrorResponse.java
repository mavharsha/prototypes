package com.example.ecommerce.api.handler;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Standard error response DTO.
 */
@Serdeable
public record ErrorResponse(String error, String message) {
}
