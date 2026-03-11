package com.example.ecommerce.common.dto;

import com.example.ecommerce.common.enums.ProductStatus;
import io.micronaut.serde.annotation.Serdeable;
import java.math.BigDecimal;

@Serdeable
public record ProductSummaryDto(
        String id,
        String name,
        String slug,
        String brandId,
        String categoryId,
        ProductStatus status,
        BigDecimal basePrice,
        String primaryImageUrl
) {}
