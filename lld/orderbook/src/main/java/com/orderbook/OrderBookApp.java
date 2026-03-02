package com.orderbook;

import com.orderbook.book.OrderBook;
import com.orderbook.model.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Demo driver with narrated scenarios for interview walkthrough.
 */
public class OrderBookApp {

    private static final OrderBook book = new OrderBook("AAPL");

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════╗");
        System.out.println("║        Order Book — LLD Demo (AAPL)          ║");
        System.out.println("╚═══════════════════════════════════════════════╝\n");

        scenario1_buildBook();
        scenario2_crossingLimitOrder();
        scenario3_marketOrder();
        scenario4_cancelOrder();
        scenario5_modifyOrder();
        scenario6_depthOfBook();
    }

    // ── Scenario 1: Build the book with resting limit orders ───

    private static void scenario1_buildBook() {
        section("Scenario 1: Place resting limit orders on both sides");

        // Bids (buy side)
        Order b1 = placeLimit(Side.BUY,  "150.00", 100);
        Order b2 = placeLimit(Side.BUY,  "149.50", 200);
        Order b3 = placeLimit(Side.BUY,  "149.00", 150);
        Order b4 = placeLimit(Side.BUY,  "150.00", 50);  // same price as b1, queued behind

        // Asks (sell side)
        Order a1 = placeLimit(Side.SELL, "151.00", 100);
        Order a2 = placeLimit(Side.SELL, "151.50", 200);
        Order a3 = placeLimit(Side.SELL, "152.00", 300);
        Order a4 = placeLimit(Side.SELL, "151.00", 75);   // same price as a1, queued behind

        book.printDepth(5);
        System.out.printf("  Best Bid: %s | Best Ask: %s | Spread: %s%n%n",
                book.getBestBid(), book.getBestAsk(), book.getSpread());
    }

    // ── Scenario 2: Crossing limit order generates trades ──────

    private static void scenario2_crossingLimitOrder() {
        section("Scenario 2: Aggressive BUY limit order crosses the spread");
        System.out.println("  Placing BUY LIMIT 120 @ 151.25");
        System.out.println("  This should fill against the ask at 151.00 (100 + 75 = 175 shares)");
        System.out.println("  with 120 qty, it fills against the first 100 at 151.00 (FIFO: a1 first)");
        System.out.println("  then 20 from a4 at 151.00\n");

        Order aggressive = Order.limitOrder(Side.BUY, new BigDecimal("151.25"), 120);
        List<Trade> trades = book.placeOrder(aggressive);

        printTrades(trades);
        System.out.printf("  Aggressive order status: %s (remaining: %d)%n%n",
                aggressive.getStatus(), aggressive.getRemainingQty());
        book.printDepth(5);
    }

    // ── Scenario 3: Market order fills at best available ───────

    private static void scenario3_marketOrder() {
        section("Scenario 3: Market SELL order — fills at best bid, IOC semantics");
        System.out.println("  Placing SELL MARKET 80 qty");
        System.out.println("  Should fill against best bid at 150.00\n");

        Order mkt = Order.marketOrder(Side.SELL, 80);
        List<Trade> trades = book.placeOrder(mkt);

        printTrades(trades);
        System.out.printf("  Market order status: %s (unfilled remainder cancelled: IOC)%n%n",
                mkt.getStatus());
        book.printDepth(5);
    }

    // ── Scenario 4: Cancel an order ────────────────────────────

    private static void scenario4_cancelOrder() {
        section("Scenario 4: Cancel a resting order");

        // Place a fresh order to cancel
        Order toCancel = Order.limitOrder(Side.BUY, new BigDecimal("148.00"), 500);
        book.placeOrder(toCancel);
        System.out.printf("  Placed order %d (BUY 500 @ 148.00)%n", toCancel.getOrderId());
        System.out.printf("  Total resting orders: %d%n", book.totalOrderCount());

        book.cancelOrder(toCancel.getOrderId());
        System.out.printf("  Cancelled order %d → status: %s%n", toCancel.getOrderId(), toCancel.getStatus());
        System.out.printf("  Total resting orders: %d%n%n", book.totalOrderCount());
    }

    // ── Scenario 5: Modify an order (cancel + replace) ────────

    private static void scenario5_modifyOrder() {
        section("Scenario 5: Modify order (cancel + replace, loses time priority)");

        Order original = Order.limitOrder(Side.SELL, new BigDecimal("153.00"), 100);
        book.placeOrder(original);
        System.out.printf("  Placed order %d (SELL 100 @ 153.00)%n", original.getOrderId());

        List<Trade> trades = book.modifyOrder(original.getOrderId(), new BigDecimal("152.50"), 150);
        System.out.printf("  Modified → SELL 150 @ 152.50 (original order %d cancelled)%n",
                original.getOrderId());
        System.out.printf("  Original status: %s%n", original.getStatus());
        printTrades(trades);
        book.printDepth(5);
    }

    // ── Scenario 6: Display depth of book ──────────────────────

    private static void scenario6_depthOfBook() {
        section("Scenario 6: Final depth of book (top 5 levels each side)");
        book.printDepth(5);
        System.out.printf("  Total resting orders in book: %d%n", book.totalOrderCount());
    }

    // ── Helpers ────────────────────────────────────────────────

    private static Order placeLimit(Side side, String price, long qty) {
        Order order = Order.limitOrder(side, new BigDecimal(price), qty);
        List<Trade> trades = book.placeOrder(order);
        System.out.printf("  [%d] %s LIMIT %d @ %s", order.getOrderId(), side, qty, price);
        if (!trades.isEmpty()) {
            System.out.printf(" → %d trade(s)", trades.size());
        }
        System.out.println();
        return order;
    }

    private static void printTrades(List<Trade> trades) {
        if (trades.isEmpty()) {
            System.out.println("  No trades.");
        } else {
            System.out.println("  Trades:");
            for (Trade t : trades) {
                System.out.printf("    %s%n", t);
            }
        }
    }

    private static void section(String title) {
        System.out.println("───────────────────────────────────────────────");
        System.out.println(title);
        System.out.println("───────────────────────────────────────────────");
    }
}
