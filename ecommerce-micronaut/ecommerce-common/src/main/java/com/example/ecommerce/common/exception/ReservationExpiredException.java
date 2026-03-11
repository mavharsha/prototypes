package com.example.ecommerce.common.exception;

/**
 * Exception thrown when a stock reservation has expired.
 */
public class ReservationExpiredException extends RuntimeException {

    private final String productId;

    public ReservationExpiredException(String productId) {
        super(String.format("Reservation expired for product: %s", productId));
        this.productId = productId;
    }

    public String getProductId() {
        return productId;
    }
}
