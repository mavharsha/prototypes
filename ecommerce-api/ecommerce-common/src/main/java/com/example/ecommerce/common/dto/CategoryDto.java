package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.time.Instant;

@Serdeable
public record CategoryDto(
        String id,
        String name,
        String description,
        String slug,
        String parentId,
        boolean active,
        int displayOrder,
        Instant createdAt,
        Instant updatedAt
) {}
