package com.example.ecommerce.domain.entity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Product domain entity.
 * Contains business logic related to products.
 */
public class Product {

    private String id;
    private String name;
    private String description;
    private BigDecimal price;
    private int stock;

    public Product() {
        this.id = UUID.randomUUID().toString();
    }

    public Product(String name, String description, BigDecimal price, int stock) {
        this();
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
    }

    // Business logic methods

    /**
     * Reduces stock when product is ordered.
     * @throws IllegalStateException if insufficient stock
     */
    public void reduceStock(int quantity) {
        if (quantity > stock) {
            throw new IllegalStateException(
                    String.format("Cannot reduce stock by %d, only %d available", quantity, stock));
        }
        this.stock -= quantity;
    }

    /**
     * Adds stock (e.g., from a return or restock).
     */
    public void addStock(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.stock += quantity;
    }

    public boolean hasStock(int quantity) {
        return stock >= quantity;
    }

    // Getters and setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }
}
