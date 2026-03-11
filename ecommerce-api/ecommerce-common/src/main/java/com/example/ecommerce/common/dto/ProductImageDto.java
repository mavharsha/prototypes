package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ProductImageDto(
        String id,
        String url,
        String altText,
        int displayOrder,
        boolean primary
) {}
