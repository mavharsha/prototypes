package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;

@Serdeable
public record PricingLineItemDto(
        String productId,
        String skuId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal subtotal
) {}
