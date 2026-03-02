package com.orderbook.book;

import com.orderbook.model.Order;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * FIFO queue of orders at a single price point.
 * Maintains price-time priority: earliest order at this price fills first.
 */
public class PriceLevel implements Iterable<Order> {

    private final BigDecimal price;
    private final LinkedList<Order> orders = new LinkedList<>();

    public PriceLevel(BigDecimal price) {
        this.price = price;
    }

    public void addOrder(Order order) {
        orders.addLast(order);
    }

    public Order peekFirst() {
        return orders.peekFirst();
    }

    public void removeFirst() {
        orders.pollFirst();
    }

    /** O(n) scan — needed for cancel-by-id within a level. */
    public boolean removeOrder(long orderId) {
        return orders.removeIf(o -> o.getOrderId() == orderId);
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    public int orderCount() {
        return orders.size();
    }

    public long totalQuantity() {
        return orders.stream().mapToLong(Order::getRemainingQty).sum();
    }

    public BigDecimal getPrice() {
        return price;
    }

    @Override
    public Iterator<Order> iterator() {
        return orders.iterator();
    }

    @Override
    public String toString() {
        return String.format("PriceLevel{price=%s, orders=%d, totalQty=%d}",
                price.toPlainString(), orderCount(), totalQuantity());
    }
}
