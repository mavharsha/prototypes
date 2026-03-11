package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.math.BigDecimal;
import java.util.List;

@Serdeable
public record UpdateProductRequest(
        String name,
        String description,
        String brandId,
        String categoryId,
        BigDecimal basePrice,
        List<ProductAttributeDto> attributes,
        String seoTitle,
        String seoDescription,
        String seoKeywords
) {}
