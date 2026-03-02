package com.orderbook.engine;

import com.orderbook.book.BookSide;
import com.orderbook.book.PriceLevel;
import com.orderbook.model.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stateless price-time priority matching engine.
 *
 * Takes an incoming order and the opposite BookSide, produces trades.
 * The OrderBook is responsible for managing the order index and resting.
 */
public class MatchingEngine {

    /**
     * Match an incoming order against the opposite side of the book.
     *
     * @param incoming     the new order to match
     * @param oppositeSide the other side of the book to match against
     * @param orderIndex   order index for removing fully-filled resting orders
     * @return list of trades generated
     */
    public List<Trade> match(Order incoming, BookSide oppositeSide,
                             Map<Long, Order> orderIndex) {
        List<Trade> trades = new ArrayList<>();

        while (incoming.getRemainingQty() > 0) {
            PriceLevel bestLevel = oppositeSide.bestLevel();
            if (bestLevel == null) break;  // no liquidity

            // For limit orders, check price crosses
            if (incoming.getType() == OrderType.LIMIT && !priceMatches(incoming, bestLevel.getPrice())) {
                break;
            }

            // Walk the level in FIFO order
            while (!bestLevel.isEmpty() && incoming.getRemainingQty() > 0) {
                Order resting = bestLevel.peekFirst();
                long fillQty = Math.min(incoming.getRemainingQty(), resting.getRemainingQty());

                // Execute fill on both sides
                incoming.fill(fillQty);
                resting.fill(fillQty);

                // Trade price = resting order's price (passive price improvement)
                long buyId  = (incoming.getSide() == Side.BUY) ? incoming.getOrderId() : resting.getOrderId();
                long sellId = (incoming.getSide() == Side.SELL) ? incoming.getOrderId() : resting.getOrderId();
                trades.add(new Trade(buyId, sellId, resting.getPrice(), fillQty));

                // Remove fully filled resting order from level and index
                if (!resting.isActive()) {
                    bestLevel.removeFirst();
                    orderIndex.remove(resting.getOrderId());
                }
            }

            // Clean up empty price level
            if (bestLevel.isEmpty()) {
                oppositeSide.removeLevel(bestLevel.getPrice());
            }
        }

        return trades;
    }

    /**
     * Check if the incoming limit order's price crosses the resting level's price.
     * BUY crosses if incoming price >= resting ask price.
     * SELL crosses if incoming price <= resting bid price.
     */
    private boolean priceMatches(Order incoming, BigDecimal restingPrice) {
        int cmp = incoming.getPrice().compareTo(restingPrice);
        return incoming.getSide() == Side.BUY ? cmp >= 0 : cmp <= 0;
    }
}
