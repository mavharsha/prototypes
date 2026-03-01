package com.example.ecommerce.domain.entity;

/**
 * Represents the lifecycle states of an order.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
