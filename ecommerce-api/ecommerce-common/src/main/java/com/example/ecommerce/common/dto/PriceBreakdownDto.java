package com.example.ecommerce.common.dto;

import com.example.ecommerce.common.enums.ShippingType;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Serdeable
public record PriceBreakdownDto(
        List<PricingLineItemDto> lineItems,
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        ShippingType shippingType,
        BigDecimal shippingCost,
        BigDecimal total,
        Instant calculatedAt
) {}
