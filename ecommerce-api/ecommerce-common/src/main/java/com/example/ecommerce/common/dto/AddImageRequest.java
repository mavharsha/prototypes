package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;

@Serdeable
public record AddImageRequest(
        @NotBlank(message = "Image URL is required")
        String url,
        String altText,
        Integer displayOrder,
        Boolean primary
) {}
