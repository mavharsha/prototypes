package com.example.ecommerce.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Order domain entity.
 * Contains business logic related to orders.
 */
public class Order {

    private String id;
    private String customerId;
    private List<OrderItem> items;
    private OrderStatus status;
    private Instant createdAt;

    public Order() {
        this.id = UUID.randomUUID().toString();
        this.items = new ArrayList<>();
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public Order(String customerId) {
        this();
        this.customerId = customerId;
    }

    // Business logic methods

    /**
     * Adds an item to the order.
     */
    public void addItem(Product product, int quantity) {
        OrderItem item = new OrderItem(product.getId(), product.getName(), quantity, product.getPrice());
        this.items.add(item);
    }

    /**
     * Calculates total order amount.
     */
    public BigDecimal calculateTotal() {
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Confirms the order.
     */
    public void confirm() {
        if (items.isEmpty()) {
            throw new IllegalStateException("Cannot confirm an empty order");
        }
        this.status = OrderStatus.CONFIRMED;
    }

    /**
     * Cancels the order.
     */
    public void cancel() {
        if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel order in status: " + status);
        }
        this.status = OrderStatus.CANCELLED;
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

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
