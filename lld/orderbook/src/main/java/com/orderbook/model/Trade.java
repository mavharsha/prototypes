package com.orderbook.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public final class Trade {

    private static final AtomicLong ID_GEN = new AtomicLong(1);

    private final long tradeId;
    private final long buyOrderId;
    private final long sellOrderId;
    private final BigDecimal price;
    private final long quantity;
    private final Instant timestamp;

    public Trade(long buyOrderId, long sellOrderId, BigDecimal price, long quantity) {
        this.tradeId = ID_GEN.getAndIncrement();
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = Instant.now();
    }

    public long getTradeId()     { return tradeId; }
    public long getBuyOrderId()  { return buyOrderId; }
    public long getSellOrderId() { return sellOrderId; }
    public BigDecimal getPrice() { return price; }
    public long getQuantity()    { return quantity; }
    public Instant getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return String.format("Trade{id=%d, buy=%d, sell=%d, price=%s, qty=%d}",
                tradeId, buyOrderId, sellOrderId, price.toPlainString(), quantity);
    }
}
