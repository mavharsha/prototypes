package com.orderbook.book;

import com.orderbook.model.Order;
import com.orderbook.model.Side;

import java.math.BigDecimal;
import java.util.*;

/**
 * One side (bid or ask) of the order book.
 *
 * Bid side: TreeMap with reverseOrder → firstEntry() = highest bid.
 * Ask side: TreeMap with naturalOrder → firstEntry() = lowest ask.
 */
public class BookSide {

    private final Side side;
    private final TreeMap<BigDecimal, PriceLevel> levels;

    public BookSide(Side side) {
        this.side = side;
        // BUY: highest price first; SELL: lowest price first
        Comparator<BigDecimal> cmp = (side == Side.BUY)
                ? Comparator.reverseOrder()
                : Comparator.naturalOrder();
        this.levels = new TreeMap<>(cmp);
    }

    /** Add an order to the appropriate price level, creating the level if needed. */
    public void addOrder(Order order) {
        levels.computeIfAbsent(order.getPrice(), PriceLevel::new)
              .addOrder(order);
    }

    /** Remove a specific order from its price level. Cleans up empty levels. */
    public boolean removeOrder(Order order) {
        PriceLevel level = levels.get(order.getPrice());
        if (level == null) return false;
        boolean removed = level.removeOrder(order.getOrderId());
        if (removed && level.isEmpty()) {
            levels.remove(order.getPrice());
        }
        return removed;
    }

    /** Best price level (highest bid or lowest ask). Null if side is empty. */
    public PriceLevel bestLevel() {
        Map.Entry<BigDecimal, PriceLevel> entry = levels.firstEntry();
        return entry != null ? entry.getValue() : null;
    }

    /** Best price on this side. Null if empty. */
    public BigDecimal bestPrice() {
        PriceLevel best = bestLevel();
        return best != null ? best.getPrice() : null;
    }

    /** Remove an entire price level (called after all orders at that price are filled). */
    public void removeLevel(BigDecimal price) {
        levels.remove(price);
    }

    /** Number of distinct price levels. */
    public int levelCount() {
        return levels.size();
    }

    public boolean isEmpty() {
        return levels.isEmpty();
    }

    /** Return the top N price levels for depth-of-book display. */
    public List<PriceLevel> topLevels(int n) {
        List<PriceLevel> result = new ArrayList<>();
        for (PriceLevel level : levels.values()) {
            if (result.size() >= n) break;
            result.add(level);
        }
        return result;
    }

    public Side getSide() {
        return side;
    }
}
