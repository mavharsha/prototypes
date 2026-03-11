package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for checkout operation.
 */
@Serdeable
public record CheckoutRequest(
        @NotNull(message = "Payment details are required")
        @Valid
        PaymentRequest payment
) {}
