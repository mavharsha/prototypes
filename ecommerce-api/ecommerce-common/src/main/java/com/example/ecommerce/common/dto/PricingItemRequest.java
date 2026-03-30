package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Serdeable
public record PricingItemRequest(
        @NotBlank(message = "Product ID is required")
        String productId,
        String skuId,
        @Positive(message = "Quantity must be positive")
        int quantity
) {}
