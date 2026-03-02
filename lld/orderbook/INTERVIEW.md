# Order Book — Interview Questions & Answers

---

## Q1. Walk me through your core data structures. Why TreeMap + LinkedList?

**Answer:**
The order book has two levels of ordering: by price and by time within a price.

- **TreeMap<BigDecimal, PriceLevel>** for price levels — gives O(log P) insert/remove and O(1) access to the best price via `firstEntry()`. Buy side uses `Comparator.reverseOrder()` so the highest bid is always first; sell side uses natural order so the lowest ask is first. A HashMap would be O(1) for lookups but can't give us the sorted order we need for best-bid/ask.

- **LinkedList<Order>** inside each PriceLevel — gives O(1) FIFO. When two orders are at the same price, the one that arrived first gets filled first (price-time priority). `addLast()` for new orders, `peekFirst()` / `removeFirst()` for matching. An ArrayList would also work for FIFO, but `removeFirst()` is O(N) on ArrayList vs O(1) on LinkedList.

The combination gives us: O(log P) to place an order, O(1) to find the best price, and O(1) to match the next order at that price. This mirrors how production exchanges (NYSE, Nasdaq) organize their books.

---

## Q2. Why did you use a separate MatchingEngine class instead of putting the logic in OrderBook?

**Answer:**
Single Responsibility — OrderBook manages book state (two sides, order index, resting orders), while MatchingEngine is a pure matching algorithm. This separation gives three benefits:

1. **Testability**: I can test matching logic in isolation by passing in a mock BookSide and order index.
2. **Swappability**: If I need a pro-rata matching algorithm (used in options markets) or an auction engine, I write a new class with the same signature — no changes to OrderBook.
3. **Readability**: OrderBook.placeOrder() reads as "match, then rest if needed" — the matching algorithm details are hidden in the engine.

The engine is intentionally stateless — `match(incoming, oppositeSide, orderIndex)` takes everything as parameters and returns a list of trades. No hidden state means no surprises.

The trade-off: passing `orderIndex` as a parameter feels slightly awkward. But it keeps MatchingEngine fully decoupled from OrderBook. In production, I'd inject the engine via constructor (`OrderBook(MatchingEngine engine)`).

---

## Q3. Why did you choose BigDecimal for prices instead of long/double?

**Answer:**
**double** has floating-point rounding: `0.1 + 0.2 = 0.30000000000000004`. In an order book, this means a limit order at $150.10 might not match against a resting order at $150.10 because their internal representations differ. This is a real bug class in financial systems.

**BigDecimal** represents decimal numbers exactly. `new BigDecimal("150.10")` is exactly 150.10, not an approximation. Comparisons are exact, arithmetic is exact, display is exact.

**long (cents/ticks)** is the production alternative — represent $150.10 as 15010 cents. Faster than BigDecimal (no object allocation, primitive arithmetic), but requires manual conversion for display and introduces a new bug class (forgetting to convert). For an interview, BigDecimal is clearer and eliminates the conversion concern entirely.

If the interviewer asks about performance: production exchanges use `long` ticks because BigDecimal allocates objects and is ~10x slower for arithmetic. The LMAX Disruptor processes 6M orders/sec with `long` prices. But correctness first, then optimize.

---

## Q4. What happens when a market order can't be fully filled?

**Answer:**
Market orders have **IOC (Immediate or Cancel) semantics**. They fill what they can against available liquidity, and any unfilled remainder is cancelled. They never rest in the book.

In the code: `OrderBook.placeOrder()` first calls `engine.match()`, which fills against the opposite side. If the order is still active after matching (has remaining quantity), we check: is it a LIMIT? → rest it in the book. Is it MARKET? → cancel it.

Why this design: a market order has no price, so there's nothing meaningful to rest at. If we left an unfilled market order in the book, it would match against any future order at any price, which would be dangerous. IOC semantics prevent this.

Real exchanges offer additional flavors: **FOK (Fill or Kill)** is all-or-nothing — either the entire quantity fills or the whole order is cancelled, no partial fills allowed. IOC is more forgiving.

---

## Q5. Why does modify lose time priority? Isn't that bad for the user?

**Answer:**
`modifyOrder()` cancels the existing order and places a new one. The new order goes to the back of the queue at its price level. This is intentional — it matches real exchange behavior at NYSE, Nasdaq, and CME.

**Why exchanges do this:** If modifying preserved time priority, participants would game it. They could place a small order early to get priority, then increase the size later. This is unfair to other participants who queued honestly. Cancel-and-replace ensures everyone competes on equal terms.

**Exception in real exchanges:** Some exchanges allow "reduce quantity only" modifications that preserve priority. If you decrease your size but keep the same price, you keep your position because you're not gaining any advantage. Our implementation doesn't support this — it would need a check like "if only qty changed and new qty < old qty, modify in-place."

**Implementation simplicity:** Cancel-and-replace is much simpler. In-place modification would need to handle: what if the new price crosses the spread? (need to match). What if the new qty is larger? (need to reset priority). Cancel-and-replace handles all cases uniformly.

---

## Q6. How would you add thread safety?

**Answer:**
The naive approach is `synchronized` or `ReadWriteLock` on OrderBook. This works but creates a bottleneck — all operations serialize through one lock, killing throughput.

**The production approach is a single-threaded event loop** (LMAX Disruptor pattern):
- One thread owns the order book — no locks needed because there's no concurrent access.
- Orders arrive via a lock-free ring buffer (pre-allocated array with CAS-based pointers).
- The owning thread drains the ring buffer in a busy-spin loop, processing orders sequentially.
- Trades and events are published to output ring buffers for downstream consumers.

This is how LMAX (6M orders/sec), Aeron, and most real exchanges work. The key insight: locks are slow because of context switches and cache invalidation. A single thread with mechanical sympathy (cache-line aligned data, no allocations in the hot path) is faster than any multi-threaded design.

For this interview implementation, single-threaded is correct because it matches the production architecture — the book itself is never accessed by multiple threads.

---

## Q7. What is the time complexity of cancel? Can you improve it?

**Answer:**
Cancel is **O(log P + N)** where P = price levels and N = orders at that price level.
- O(1): lookup order by ID in the HashMap order index.
- O(log P): find the price level in the TreeMap (implicit in `BookSide.removeOrder`).
- O(N): scan the LinkedList at that price level to find and remove the order.

The O(N) scan is the bottleneck. To improve it:

1. **Doubly-linked list with node references**: Store a reference to the LinkedList node in the order index. Then removal is O(1) — just unlink the node. Java's `LinkedList` doesn't expose node references, so you'd need a custom doubly-linked list. This is what production exchanges do.

2. **Order knows its PriceLevel**: Store a back-reference from Order to its PriceLevel and its position. This makes cancel O(1) but adds coupling between Order and PriceLevel.

With approach #1, cancel drops to **O(1)** — HashMap lookup + node unlink. This is the standard production optimization.

---

## Q8. How would you implement a pro-rata matching algorithm?

**Answer:**
Pro-rata allocates fills proportionally by order size rather than FIFO. It's used in options markets (CME options) where market makers rest large orders and expect proportional fills.

Implementation:
1. Create a `ProRataMatchingEngine` with the same `match()` signature.
2. Instead of walking the PriceLevel in FIFO order, calculate each order's share: `orderQty / totalLevelQty * fillQty`.
3. Handle rounding (allocate remainder to the largest order, or FIFO for the residual).
4. Inject it into OrderBook: `new OrderBook("AAPL", new ProRataMatchingEngine())`.

The architecture supports this cleanly because MatchingEngine is stateless and decoupled from OrderBook. The only change to OrderBook is accepting the engine via constructor injection (one line).

Hybrid algorithms also exist: CME uses "FIFO with pro-rata for market makers" — the top order in the queue gets priority, then the rest is pro-rata. This would be a third engine implementation.

---

## Q9. What's the spread and why does it matter?

**Answer:**
The spread is `bestAsk - bestBid` — the gap between the highest someone is willing to pay and the lowest someone is willing to sell. In our code: `getSpread()` returns `asks.bestPrice() - bids.bestPrice()`.

It matters because:
- **Liquidity indicator**: A tight spread (e.g., $0.01) means high liquidity — many competing orders. A wide spread means low liquidity.
- **Transaction cost**: If you want to buy immediately, you cross the spread by paying the ask price. The spread is effectively the cost of immediate execution.
- **Market maker profit**: Market makers rest orders on both sides and profit from the spread. A $0.01 spread on 100 shares = $1 per round trip.

If the spread is ever negative (bid > ask), it means orders should match — this is called a "crossed book" and our matching engine handles it automatically. When an aggressive buy limit at $151.25 arrives and the best ask is $151.00, the engine matches them at $151.00 (passive price improvement for the buyer).

---

## Q10. How would you add event sourcing / persistence?

**Answer:**
Event sourcing means storing every state change as an immutable event, then rebuilding state by replaying events.

Events for the order book:
- `OrderPlaced(orderId, side, type, price, qty, timestamp)`
- `OrderFilled(orderId, fillQty, fillPrice, tradeId, timestamp)`
- `OrderCancelled(orderId, timestamp)`
- `TradeExecuted(tradeId, buyId, sellId, price, qty, timestamp)`

Implementation:
1. **Write-ahead log**: Before applying any operation, append the event to a sequential log file (or Kafka topic).
2. **Apply to in-memory state**: Then apply the event to the OrderBook as we do now.
3. **Recovery**: On startup, replay the event log from the beginning to rebuild the book.
4. **Snapshots**: Periodically serialize the full book state. On recovery, load the latest snapshot then replay events after the snapshot.

Benefits:
- Full audit trail — can answer "what was the book state at time T?"
- Recovery from crashes — replay the log.
- Replication — stream events to replica nodes for read scaling.

This is how most production exchanges work. The event log is the source of truth, not the in-memory state.

---

## Q11. Your BookSide uses the same class for both buy and sell. Why not separate classes?

**Answer:**
The only difference between the buy side and sell side is the sort direction. Buy side sorts prices descending (highest bid first), sell side sorts ascending (lowest ask first). A `Comparator` parameter captures this difference exactly.

If I had separate `BidSide` and `AskSide` classes:
- 90% of the code would be identical (addOrder, removeOrder, bestLevel, topLevels).
- Any bug fix or feature would need to be applied in two places.
- The only different line is the TreeMap constructor: `new TreeMap<>(Comparator.reverseOrder())` vs `new TreeMap<>()`.

This is DRY — the comparator is the Strategy pattern at its simplest. One class, one parameter, zero duplication.

The same principle applies to PriceLevel — there's no "bid PriceLevel" vs "ask PriceLevel." FIFO ordering is the same regardless of side.

---

## Q12. Walk me through what happens when a BUY LIMIT 120 @ $151.25 hits a book with ASK 100 @ $151.00 and ASK 75 @ $151.00.

**Answer:**
Step by step through `MatchingEngine.match()`:

1. **Check opposite side**: Best ask is $151.00. Incoming buy price $151.25 >= resting ask $151.00 → prices cross, so we match.

2. **First fill**: Resting order a1 has 100 qty. Incoming has 120 remaining. `fillQty = min(120, 100) = 100`. Both orders get filled: incoming has 20 remaining, a1 is fully filled. Trade created: 100 shares @ $151.00 (resting order's price — passive price improvement for the buyer). a1 is removed from the price level and the order index.

3. **Second fill**: Same price level, next order a4 has 75 qty. Incoming has 20 remaining. `fillQty = min(20, 75) = 20`. Incoming is fully filled (status → FILLED), a4 has 55 remaining (status → PARTIALLY_FILLED). Trade created: 20 shares @ $151.00. a4 stays in the level (still has 55 remaining).

4. **Incoming fully filled**: Loop exits. Returns 2 trades.

5. **Back in OrderBook.placeOrder()**: Incoming is no longer active (FILLED), so it doesn't rest in the book. The 55 remaining on a4 stays at $151.00 as the new best ask.

Key points demonstrated: FIFO (a1 before a4), partial fills, passive price improvement ($151.25 order got filled at $151.00), resting order survives partial fill.
