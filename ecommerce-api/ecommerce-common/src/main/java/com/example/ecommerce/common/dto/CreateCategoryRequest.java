package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record CreateCategoryRequest(
        @NotBlank(message = "Category name is required")
        String name,
        String description,
        String parentId,
        Integer displayOrder
) {}
