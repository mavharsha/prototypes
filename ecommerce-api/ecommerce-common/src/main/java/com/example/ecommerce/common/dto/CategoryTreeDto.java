package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;

@Serdeable
public record CategoryTreeDto(
        String id,
        String name,
        String slug,
        boolean active,
        int displayOrder,
        List<CategoryTreeDto> children
) {}
