package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Data Transfer Object for Product.
 * Used for API requests/responses - decoupled from domain entities.
 */
@Serdeable
public record ProductDto(
        String id,

        @NotBlank(message = "Product name is required")
        String name,

        String description,

        @Positive(message = "Price must be positive")
        BigDecimal price,

        @Positive(message = "Stock must be positive")
        Integer stock
) {
    /**
     * Factory method for creating a new product (without ID)
     */
    public static ProductDto create(String name, String description, BigDecimal price, Integer stock) {
        return new ProductDto(null, name, description, price, stock);
    }
}
