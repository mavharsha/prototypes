package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.Map;

@Serdeable
public record CreateSkuRequest(
        @NotBlank(message = "SKU code is required")
        String skuCode,
        BigDecimal priceOverride,
        @PositiveOrZero(message = "Stock quantity must be zero or positive")
        int stockQuantity,
        Integer lowStockThreshold,
        Map<String, String> attributes,
        String barcode,
        Double weight,
        Double dimensionLength,
        Double dimensionWidth,
        Double dimensionHeight
) {}
