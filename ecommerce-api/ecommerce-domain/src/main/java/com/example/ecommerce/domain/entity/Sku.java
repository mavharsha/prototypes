package com.example.ecommerce.domain.entity;

import com.example.ecommerce.common.enums.StockStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Sku {
    private String id;
    private String productId;
    private String skuCode;
    private BigDecimal priceOverride;
    private int stockQuantity;
    private int lowStockThreshold;
    private Map<String, String> attributes;
    private String barcode;
    private Double weight;
    private Double dimensionLength;
    private Double dimensionWidth;
    private Double dimensionHeight;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public Sku() {
        this.id = UUID.randomUUID().toString();
        this.lowStockThreshold = 5;
        this.attributes = new HashMap<>();
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Sku(String productId, String skuCode, int stockQuantity) {
        this();
        this.productId = productId;
        this.skuCode = skuCode;
        this.stockQuantity = stockQuantity;
    }

    // Business logic
    public BigDecimal getEffectivePrice(BigDecimal basePrice) {
        return priceOverride != null ? priceOverride : basePrice;
    }

    public StockStatus getStockStatus() {
        if (stockQuantity <= 0) return StockStatus.OUT_OF_STOCK;
        if (stockQuantity <= lowStockThreshold) return StockStatus.LOW_STOCK;
        return StockStatus.IN_STOCK;
    }

    public void reduceStock(int quantity) {
        if (quantity > stockQuantity) {
            throw new IllegalStateException(
                    String.format("Cannot reduce stock by %d, only %d available for SKU %s", quantity, stockQuantity, skuCode));
        }
        this.stockQuantity -= quantity;
        this.updatedAt = Instant.now();
    }

    public void addStock(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.stockQuantity += quantity;
        this.updatedAt = Instant.now();
    }

    public boolean hasStock(int quantity) {
        return stockQuantity >= quantity;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; this.updatedAt = Instant.now(); }
    public BigDecimal getPriceOverride() { return priceOverride; }
    public void setPriceOverride(BigDecimal priceOverride) { this.priceOverride = priceOverride; this.updatedAt = Instant.now(); }
    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) { this.stockQuantity = stockQuantity; this.updatedAt = Instant.now(); }
    public int getLowStockThreshold() { return lowStockThreshold; }
    public void setLowStockThreshold(int lowStockThreshold) { this.lowStockThreshold = lowStockThreshold; this.updatedAt = Instant.now(); }
    public Map<String, String> getAttributes() { return attributes; }
    public void setAttributes(Map<String, String> attributes) { this.attributes = attributes; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
    public Double getDimensionLength() { return dimensionLength; }
    public void setDimensionLength(Double dimensionLength) { this.dimensionLength = dimensionLength; }
    public Double getDimensionWidth() { return dimensionWidth; }
    public void setDimensionWidth(Double dimensionWidth) { this.dimensionWidth = dimensionWidth; }
    public Double getDimensionHeight() { return dimensionHeight; }
    public void setDimensionHeight(Double dimensionHeight) { this.dimensionHeight = dimensionHeight; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; this.updatedAt = Instant.now(); }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
