package com.example.ecommerce.common.dto;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Data Transfer Object for Payment.
 */
@Serdeable
public record PaymentDto(
        String id,
        String orderId,
        String customerId,
        BigDecimal amount,
        String status,
        String maskedCardNumber,
        Instant createdAt
) {}
