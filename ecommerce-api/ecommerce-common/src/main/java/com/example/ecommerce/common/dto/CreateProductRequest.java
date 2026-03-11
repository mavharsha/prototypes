package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

@Serdeable
public record CreateProductRequest(
        @NotBlank(message = "Product name is required")
        String name,
        String description,
        String brandId,
        String categoryId,
        @Positive(message = "Base price must be positive")
        BigDecimal basePrice,
        List<ProductAttributeDto> attributes,
        String seoTitle,
        String seoDescription,
        String seoKeywords
) {}
