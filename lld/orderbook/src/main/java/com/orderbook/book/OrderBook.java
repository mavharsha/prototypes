package com.orderbook.book;

import com.orderbook.engine.MatchingEngine;
import com.orderbook.exception.InvalidOrderException;
import com.orderbook.exception.OrderNotFoundException;
import com.orderbook.model.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central order book facade for a single symbol.
 *
 * Manages bid/ask sides, an order index for O(1) lookups,
 * and delegates matching to the stateless MatchingEngine.
 */
public class OrderBook {

    private final String symbol;
    private final BookSide bids = new BookSide(Side.BUY);
    private final BookSide asks = new BookSide(Side.SELL);
    private final Map<Long, Order> orderIndex = new HashMap<>();
    private final MatchingEngine engine = new MatchingEngine();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    // ── Place ──────────────────────────────────────────────────

    public List<Trade> placeOrder(Order order) {
        if (!order.isActive()) {
            throw new InvalidOrderException("Cannot place an inactive order");
        }

        BookSide sameSide = (order.getSide() == Side.BUY) ? bids : asks;
        BookSide oppositeSide = (order.getSide() == Side.BUY) ? asks : bids;

        // Match against opposite side
        List<Trade> trades = engine.match(order, oppositeSide, orderIndex);

        // Rest any remaining quantity (limit orders only)
        if (order.isActive()) {
            if (order.getType() == OrderType.LIMIT) {
                sameSide.addOrder(order);
                orderIndex.put(order.getOrderId(), order);
            } else {
                // Market orders never rest — cancel unfilled remainder (IOC semantics)
                order.cancel();
            }
        }

        return trades;
    }

    // ── Cancel ─────────────────────────────────────────────────

    public void cancelOrder(long orderId) {
        Order order = orderIndex.get(orderId);
        if (order == null) {
            throw new OrderNotFoundException(orderId);
        }

        BookSide side = (order.getSide() == Side.BUY) ? bids : asks;
        side.removeOrder(order);
        orderIndex.remove(orderId);
        order.cancel();
    }

    // ── Modify (cancel + replace) ──────────────────────────────

    /**
     * Modify = Cancel + Replace. Intentionally loses time priority.
     * This is the industry standard at NYSE, Nasdaq, CME.
     */
    public List<Trade> modifyOrder(long orderId, BigDecimal newPrice, long newQty) {
        Order existing = orderIndex.get(orderId);
        if (existing == null) {
            throw new OrderNotFoundException(orderId);
        }

        // Cancel existing
        cancelOrder(orderId);

        // Place replacement
        Order replacement = Order.limitOrder(existing.getSide(), newPrice, newQty);
        return placeOrder(replacement);
    }

    // ── Queries ────────────────────────────────────────────────

    public Order getOrder(long orderId) {
        Order order = orderIndex.get(orderId);
        if (order == null) throw new OrderNotFoundException(orderId);
        return order;
    }

    public BigDecimal getBestBid() {
        return bids.bestPrice();
    }

    public BigDecimal getBestAsk() {
        return asks.bestPrice();
    }

    public BigDecimal getSpread() {
        BigDecimal bid = getBestBid();
        BigDecimal ask = getBestAsk();
        if (bid == null || ask == null) return null;
        return ask.subtract(bid);
    }

    public List<PriceLevel> getBidDepth(int levels) {
        return bids.topLevels(levels);
    }

    public List<PriceLevel> getAskDepth(int levels) {
        return asks.topLevels(levels);
    }

    public int totalOrderCount() {
        return orderIndex.size();
    }

    public String getSymbol() {
        return symbol;
    }

    // ── Display ────────────────────────────────────────────────

    public void printDepth(int levels) {
        System.out.println("\n═══════════════════════════════════════════");
        System.out.printf("  Order Book: %s%n", symbol);
        System.out.println("═══════════════════════════════════════════");

        List<PriceLevel> askLevels = getAskDepth(levels);
        // Print asks in reverse so the lowest ask is closest to the spread
        for (int i = askLevels.size() - 1; i >= 0; i--) {
            PriceLevel level = askLevels.get(i);
            System.out.printf("  ASK  %10s  |  %-6d  (%d orders)%n",
                    level.getPrice().toPlainString(), level.totalQuantity(), level.orderCount());
        }

        System.out.println("  ─────────────────┼──────────────────────");

        BigDecimal spread = getSpread();
        System.out.printf("  Spread: %s%n", spread != null ? spread.toPlainString() : "N/A");

        System.out.println("  ─────────────────┼──────────────────────");

        for (PriceLevel level : getBidDepth(levels)) {
            System.out.printf("  BID  %10s  |  %-6d  (%d orders)%n",
                    level.getPrice().toPlainString(), level.totalQuantity(), level.orderCount());
        }

        System.out.println("═══════════════════════════════════════════\n");
    }
}
