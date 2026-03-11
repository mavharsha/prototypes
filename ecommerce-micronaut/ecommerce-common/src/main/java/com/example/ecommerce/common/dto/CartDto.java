package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Data Transfer Object for Cart.
 */
@Serdeable
public record CartDto(
        String id,
        String customerId,
        List<CartItemDto> items,
        BigDecimal totalAmount,
        Instant updatedAt
) {
    @Serdeable
    public record CartItemDto(
            String productId,
            String productName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal
    ) {}
}
