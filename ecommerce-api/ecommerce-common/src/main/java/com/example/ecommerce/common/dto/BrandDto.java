package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

@Serdeable
public record BrandDto(
        String id,
        String name,
        String description,
        String logoUrl,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {}
