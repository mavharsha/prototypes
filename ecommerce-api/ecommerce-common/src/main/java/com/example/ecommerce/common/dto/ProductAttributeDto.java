package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ProductAttributeDto(
        String key,
        String value
) {}
