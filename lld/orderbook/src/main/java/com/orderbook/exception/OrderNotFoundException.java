package com.orderbook.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(long orderId) {
        super("Order not found: " + orderId);
    }
}
