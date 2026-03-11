package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record UpdateCategoryRequest(
        String name,
        String description,
        Boolean active,
        Integer displayOrder
) {}
