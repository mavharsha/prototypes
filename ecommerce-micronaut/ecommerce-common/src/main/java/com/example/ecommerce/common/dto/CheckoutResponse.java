package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;

/**
 * Response DTO for checkout operation.
 */
@Serdeable
public record CheckoutResponse(
        OrderDto order,
        PaymentDto payment,
        String message
) {}
