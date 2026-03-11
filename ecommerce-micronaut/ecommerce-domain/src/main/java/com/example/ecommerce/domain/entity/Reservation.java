package com.example.ecommerce.domain.entity;

import java.time.Instant;
import java.util.UUID;

/**
 * Stock reservation domain entity.
 * Tracks reserved product stock with expiry.
 */
public class Reservation {

    private String id;
    private String customerId;
    private String productId;
    private int quantity;
    private Instant createdAt;
    private Instant expiresAt;
    private ReservationStatus status;

    public Reservation() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.status = ReservationStatus.ACTIVE;
    }

    public Reservation(String customerId, String productId, int quantity, long expiryMinutes) {
        this();
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.expiresAt = createdAt.plusSeconds(expiryMinutes * 60);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
    }

    public void confirm() {
        this.status = ReservationStatus.CONFIRMED;
    }

    public void expire() {
        this.status = ReservationStatus.EXPIRED;
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }
}
