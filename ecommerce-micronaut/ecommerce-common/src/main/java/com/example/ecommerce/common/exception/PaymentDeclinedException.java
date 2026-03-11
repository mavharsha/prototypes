package com.example.ecommerce.common.exception;

/**
 * Exception thrown when a payment is declined.
 */
public class PaymentDeclinedException extends RuntimeException {

    private final String reason;

    public PaymentDeclinedException(String reason) {
        super(String.format("Payment declined: %s", reason));
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
