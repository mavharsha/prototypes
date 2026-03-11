package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record CreateBrandRequest(
        @NotBlank(message = "Brand name is required")
        String name,
        String description,
        String logoUrl
) {}
