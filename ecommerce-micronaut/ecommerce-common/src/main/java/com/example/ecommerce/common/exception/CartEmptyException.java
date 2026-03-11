package com.example.ecommerce.common.exception;

/**
 * Exception thrown when attempting to checkout with an empty cart.
 */
public class CartEmptyException extends RuntimeException {

    private final String customerId;

    public CartEmptyException(String customerId) {
        super(String.format("Cart is empty for customer: %s", customerId));
        this.customerId = customerId;
    }

    public String getCustomerId() {
        return customerId;
    }
}
