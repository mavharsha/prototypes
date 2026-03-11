package com.example.ecommerce.common.dto;

import com.example.ecommerce.common.enums.ProductStatus;
import io.micronaut.serde.annotation.Serdeable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Serdeable
public record ProductDto(
        String id,
        String name,
        String description,
        String slug,
        String brandId,
        String categoryId,
        ProductStatus status,
        BigDecimal basePrice,
        List<ProductImageDto> images,
        List<ProductAttributeDto> attributes,
        String seoTitle,
        String seoDescription,
        String seoKeywords,
        Instant createdAt,
        Instant updatedAt
) {}
