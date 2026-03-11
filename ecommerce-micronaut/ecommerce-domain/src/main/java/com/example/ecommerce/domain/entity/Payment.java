package com.example.ecommerce.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payment domain entity.
 * Tracks payment transactions for orders.
 */
public class Payment {

    private String id;
    private String orderId;
    private String customerId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String maskedCardNumber;
    private Instant createdAt;

    public Payment() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.status = PaymentStatus.PENDING;
    }

    public Payment(String orderId, String customerId, BigDecimal amount, String maskedCardNumber) {
        this();
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.maskedCardNumber = maskedCardNumber;
    }

    public void approve() {
        this.status = PaymentStatus.APPROVED;
    }

    public void decline() {
        this.status = PaymentStatus.DECLINED;
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getMaskedCardNumber() {
        return maskedCardNumber;
    }

    public void setMaskedCardNumber(String maskedCardNumber) {
        this.maskedCardNumber = maskedCardNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
