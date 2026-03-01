package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Data Transfer Object for Order.
 */
@Serdeable
public record OrderDto(
        String id,

        @NotBlank(message = "Customer ID is required")
        String customerId,

        @NotEmpty(message = "Order must have at least one item")
        List<OrderItemDto> items,

        BigDecimal totalAmount,

        String status,

        Instant createdAt
) {
    @Serdeable
    public record OrderItemDto(
            String productId,
            String productName,
            Integer quantity,
            BigDecimal unitPrice
    ) {}
}
