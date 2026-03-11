package com.example.ecommerce.common.dto;

import com.example.ecommerce.common.enums.StockStatus;
import io.micronaut.serde.annotation.Serdeable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Serdeable
public record SkuDto(
        String id,
        String productId,
        String skuCode,
        BigDecimal priceOverride,
        BigDecimal effectivePrice,
        int stockQuantity,
        int lowStockThreshold,
        StockStatus stockStatus,
        Map<String, String> attributes,
        String barcode,
        Double weight,
        Double dimensionLength,
        Double dimensionWidth,
        Double dimensionHeight,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
