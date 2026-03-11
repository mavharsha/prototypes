package com.example.ecommerce.common.exception;

public class InsufficientStockException extends RuntimeException {
    private final String productId;
    private final String skuCode;
    private final int requested;
    private final int available;

    public InsufficientStockException(String productId, String skuCode, int requested, int available) {
        super(String.format("Insufficient stock for product %s (SKU: %s): requested %d, available %d",
                productId, skuCode, requested, available));
        this.productId = productId;
        this.skuCode = skuCode;
        this.requested = requested;
        this.available = available;
    }

    public String getProductId() { return productId; }
    public String getSkuCode() { return skuCode; }
    public int getRequested() { return requested; }
    public int getAvailable() { return available; }
}
