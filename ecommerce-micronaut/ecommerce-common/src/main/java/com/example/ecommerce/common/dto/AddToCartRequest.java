package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Request DTO for adding an item to the cart.
 */
@Serdeable
public record AddToCartRequest(
        @NotBlank(message = "Product ID is required")
        String productId,

        @Positive(message = "Quantity must be positive")
        int quantity
) {}
