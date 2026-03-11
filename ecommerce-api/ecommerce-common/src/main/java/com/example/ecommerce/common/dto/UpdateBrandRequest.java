package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record UpdateBrandRequest(
        String name,
        String description,
        String logoUrl,
        Boolean active
) {}
