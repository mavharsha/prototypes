package com.orderbook.model;

import com.orderbook.exception.InvalidOrderException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class Order {

    private static final AtomicLong ID_GEN = new AtomicLong(1);

    private final long orderId;
    private final Side side;
    private final OrderType type;
    private final BigDecimal price;   // null for MARKET orders
    private final long originalQty;
    private long filledQty;
    private OrderStatus status;
    private final Instant timestamp;

    private Order(Side side, OrderType type, BigDecimal price, long quantity) {
        this.orderId = ID_GEN.getAndIncrement();
        this.side = side;
        this.type = type;
        this.price = price;
        this.originalQty = quantity;
        this.filledQty = 0;
        this.status = OrderStatus.NEW;
        this.timestamp = Instant.now();
    }

    // ── Factory methods ────────────────────────────────────────

    public static Order limitOrder(Side side, BigDecimal price, long quantity) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderException("Limit order requires a positive price");
        }
        if (quantity <= 0) {
            throw new InvalidOrderException("Quantity must be positive");
        }
        return new Order(side, OrderType.LIMIT, price, quantity);
    }

    public static Order marketOrder(Side side, long quantity) {
        if (quantity <= 0) {
            throw new InvalidOrderException("Quantity must be positive");
        }
        return new Order(side, OrderType.MARKET, null, quantity);
    }

    // ── Lifecycle ──────────────────────────────────────────────

    public void fill(long qty) {
        if (qty <= 0 || qty > getRemainingQty()) {
            throw new InvalidOrderException(
                    "Invalid fill qty " + qty + " for remaining " + getRemainingQty());
        }
        this.filledQty += qty;
        this.status = (filledQty == originalQty) ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
    }

    public void cancel() {
        if (status == OrderStatus.FILLED) {
            throw new InvalidOrderException("Cannot cancel a fully filled order");
        }
        this.status = OrderStatus.CANCELLED;
    }

    // ── Queries ────────────────────────────────────────────────

    public long getRemainingQty() {
        return originalQty - filledQty;
    }

    public boolean isActive() {
        return status == OrderStatus.NEW || status == OrderStatus.PARTIALLY_FILLED;
    }

    // ── Getters ────────────────────────────────────────────────

    public long getOrderId()       { return orderId; }
    public Side getSide()          { return side; }
    public OrderType getType()     { return type; }
    public BigDecimal getPrice()   { return price; }
    public long getOriginalQty()   { return originalQty; }
    public long getFilledQty()     { return filledQty; }
    public OrderStatus getStatus() { return status; }
    public Instant getTimestamp()   { return timestamp; }

    @Override
    public String toString() {
        return String.format("Order{id=%d, %s %s, price=%s, qty=%d/%d, %s}",
                orderId, side, type,
                price != null ? price.toPlainString() : "MKT",
                filledQty, originalQty, status);
    }
}
