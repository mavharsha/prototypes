package com.example.ecommerce.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Shopping cart domain entity.
 * Contains business logic for managing cart items.
 */
public class Cart {

    private String id;
    private String customerId;
    private List<CartItem> items;
    private Instant createdAt;
    private Instant updatedAt;

    public Cart() {
        this.id = UUID.randomUUID().toString();
        this.items = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Cart(String customerId) {
        this();
        this.customerId = customerId;
    }

    /**
     * Adds an item to the cart. If the product already exists, increases quantity.
     */
    public void addItem(String productId, String productName, int quantity, BigDecimal unitPrice) {
        Optional<CartItem> existing = findItem(productId);
        if (existing.isPresent()) {
            CartItem item = existing.get();
            item.setQuantity(item.getQuantity() + quantity);
        } else {
            items.add(new CartItem(productId, productName, quantity, unitPrice));
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Removes an item from the cart by product ID.
     */
    public void removeItem(String productId) {
        items.removeIf(item -> item.getProductId().equals(productId));
        this.updatedAt = Instant.now();
    }

    /**
     * Updates the quantity of a specific item.
     */
    public void updateItemQuantity(String productId, int newQuantity) {
        findItem(productId).ifPresent(item -> {
            item.setQuantity(newQuantity);
            this.updatedAt = Instant.now();
        });
    }

    /**
     * Clears all items from the cart.
     */
    public void clear() {
        items.clear();
        this.updatedAt = Instant.now();
    }

    public Optional<CartItem> findItem(String productId) {
        return items.stream()
                .filter(item -> item.getProductId().equals(productId))
                .findFirst();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public BigDecimal calculateTotal() {
        return items.stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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

    public List<CartItem> getItems() {
        return items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
