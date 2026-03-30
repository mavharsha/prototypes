package com.example.ecommerce.common.dto;

import com.example.ecommerce.common.enums.ShippingType;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Serdeable
public record CalculatePriceRequest(
        @NotNull(message = "Items are required")
        @Size(min = 1, message = "At least one item is required")
        List<@Valid PricingItemRequest> items,
        @NotNull(message = "Shipping type is required")
        ShippingType shippingType
) {}
