# Order Book — Interview Navigation Flow

> Step-by-step guide for walking an interviewer through the codebase.
> Each stop references exact files and lines. Spend ~2 min per stop.

---

## Overview (draw on whiteboard first)

```
                     ┌─────────────────────────────────┐
                     │           OrderBook              │  ← Facade
                     │  (placeOrder, cancelOrder,       │
                     │   modifyOrder, queries)          │
                     └──┬────────┬──────────┬──────────┘
                        │        │          │
             ┌──────────▼──┐  ┌──▼────────┐ │
             │  BookSide   │  │  Matching  │ │
             │  (BUY/SELL) │  │  Engine    │ │
             │  TreeMap     │  │ (stateless)│ │
             └──────┬───────┘  └───────────┘ │
                    │                        │
          ┌─────────▼──────────┐   ┌─────────▼──────────┐
          │ PriceLevel         │   │ orderIndex          │
          │ LinkedList<Order>  │   │ Map<Long, Order>    │
          │ (FIFO at 1 price)  │   │ (O(1) cancel/modify)│
          └────────────────────┘   └────────────────────┘

   Book structure (AAPL):
     ASK  152.00  |  300 qty  (3rd best)
     ASK  151.50  |  200 qty  (2nd best)
     ASK  151.00  |  175 qty  (best ask) ← 2 orders: 100 + 75 (FIFO)
     ─────────── spread: 1.00 ───────────
     BID  150.00  |  150 qty  (best bid) ← 2 orders: 100 + 50 (FIFO)
     BID  149.50  |  200 qty
     BID  149.00  |  150 qty
```

**Say:** *"An order book for AAPL. Two sides — bids (buy) sorted highest-first, asks (sell) sorted lowest-first. Each price level is a FIFO queue. Matching engine is stateless — takes an incoming order and the opposite side, produces trades. HashMap index for O(1) cancel."*

---

## Stop 1 — Enums (30 sec)

**Files:**
- `model/Side.java:3-4` — `BUY | SELL`
- `model/OrderType.java:3-4` — `LIMIT | MARKET`
- `model/OrderStatus.java:3-4` — `NEW | PARTIALLY_FILLED | FILLED | CANCELLED`

**Say:** *"Three enums define the vocabulary. OrderStatus is a state machine: NEW → PARTIALLY_FILLED → FILLED, or CANCELLED from any active state. Transitions are enforced in Order.fill() and Order.cancel()."*

**Draw state machine:**
```
NEW ──fill(partial)──→ PARTIALLY_FILLED ──fill(remaining)──→ FILLED
 │                           │
 └───cancel()───────────────→ CANCELLED ←───cancel()────────┘
```

---

## Stop 2 — Order model

**File:** `model/Order.java`

**Key lines:**
- `:11` — AtomicLong ID generation
- `:16` — `BigDecimal price` — null for MARKET orders
- `:18-19` — Mutable state: `filledQty`, `status`
- `:22-31` — Private constructor: sets `status = NEW`, `filledQty = 0`
- `:35-43` — `limitOrder(side, price, qty)` — validates positive price + qty
- `:45-50` — `marketOrder(side, qty)` — price is null, validates positive qty
- `:54-61` — `fill(qty)` — the critical state transition:
  ```java
  this.filledQty += qty;
  this.status = (filledQty == originalQty) ? FILLED : PARTIALLY_FILLED;
  ```
- `:63-68` — `cancel()` — prevents cancelling a fully filled order
- `:72-74` — `getRemainingQty()` = `originalQty - filledQty`

**Say:** *"Order has immutable identity (id, side, type, price, qty) and mutable fill state (filledQty, status). Factory methods enforce invariants — you can't create an order with negative price or zero quantity. The fill() method is called by the matching engine; it updates filled quantity and transitions the status automatically."*

**Interviewer might ask:** Why BigDecimal?
**Answer:** `0.1 + 0.2 != 0.3` in floating-point. Financial systems need exact decimal arithmetic. BigDecimal represents 150.10 exactly. Production systems use `long` ticks (150.10 → 15010) for speed, but BigDecimal is clearer in an interview.

---

## Stop 3 — Trade (immutable record)

**File:** `model/Trade.java`

**Key lines:**
- `:7` — `final class` — completely immutable
- `:13-16` — Fields: `buyOrderId`, `sellOrderId`, `price`, `quantity`
- `:18-25` — Constructor: AtomicLong ID + `Instant.now()` timestamp

**Say:** *"Trade is the output of matching. Immutable — once created, never modified. It records what happened: which buy and sell orders matched, at what price, for how much quantity. The price is always the resting order's price — passive price improvement for the aggressor."*

Quick stop — 30 seconds max.

---

## Stop 4 — PriceLevel (FIFO queue at one price)

**File:** `book/PriceLevel.java`

**Key lines:**
- `:16` — `LinkedList<Order>` — the FIFO queue
- `:22-24` — `addOrder()` → `orders.addLast()` — O(1) enqueue
- `:26-28` — `peekFirst()` → `orders.peekFirst()` — O(1) peek
- `:30-32` — `removeFirst()` → `orders.pollFirst()` — O(1) dequeue
- `:35-37` — `removeOrder(orderId)` → `removeIf()` — O(N) scan for cancel
- `:13` — `implements Iterable<Order>` — exposes iteration without leaking LinkedList

**Say:** *"PriceLevel wraps a LinkedList with domain methods. addLast/peekFirst/removeFirst gives FIFO in O(1). This enforces price-time priority — the earliest order at this price fills first. The wrapper prevents callers from inserting in the middle or breaking FIFO invariants."*

**Interviewer might ask:** removeOrder is O(N) — can you do better?
**Answer:** Yes. Use a custom doubly-linked list and store node references in the order index. Then cancel is O(1) — just unlink the node. Java's LinkedList doesn't expose nodes, so you'd need a custom implementation. That's the standard production optimization.

---

## Stop 5 — BookSide (one side of the book) ← KEY DATA STRUCTURE

**File:** `book/BookSide.java`

**Key lines:**
- `:18` — `TreeMap<BigDecimal, PriceLevel>` — sorted price levels
- `:20-27` — Constructor — **Strategy via Comparator**:
  ```java
  Comparator<BigDecimal> cmp = (side == Side.BUY)
      ? Comparator.reverseOrder()      // highest bid first
      : Comparator.naturalOrder();     // lowest ask first
  this.levels = new TreeMap<>(cmp);
  ```
- `:30-33` — `addOrder()`:
  ```java
  levels.computeIfAbsent(order.getPrice(), PriceLevel::new)
        .addOrder(order);
  ```
- `:36-44` — `removeOrder()` — finds level by price, removes order, cleans empty level
- `:47-50` — `bestLevel()` → `levels.firstEntry()` — O(1) best price
- `:59-61` — `removeLevel()` — called by matching engine after draining a level

**Say:** *"TreeMap sorts price levels. The BUY side uses reverseOrder so firstEntry() returns the highest bid. The SELL side uses naturalOrder so firstEntry() returns the lowest ask. Same class, same code, different comparator — this is the Strategy pattern. No BidSide/AskSide duplication."*

**Draw the data structure:**
```
BUY side TreeMap (reverseOrder):
  150.00 → PriceLevel [Order(100), Order(50)]   ← firstEntry() = best bid
  149.50 → PriceLevel [Order(200)]
  149.00 → PriceLevel [Order(150)]

ASK side TreeMap (naturalOrder):
  151.00 → PriceLevel [Order(100), Order(75)]    ← firstEntry() = best ask
  151.50 → PriceLevel [Order(200)]
  152.00 → PriceLevel [Order(300)]
```

**Key insight:** `computeIfAbsent` at `:31` — creates a new PriceLevel if this price doesn't exist yet, otherwise appends to the existing FIFO queue. One line handles both cases.

---

## Stop 6 — MatchingEngine (stateless, the core algorithm) ← SPEND THE MOST TIME

**File:** `engine/MatchingEngine.java`

### 6a. Signature (`:28-29`)

```java
public List<Trade> match(Order incoming, BookSide oppositeSide,
                         Map<Long, Order> orderIndex)
```

**Say:** *"Pure function — takes everything as parameters, returns trades. No internal state. This makes it trivially testable and swappable (pro-rata, auction engine, etc.)."*

### 6b. The matching loop (`:30-68`) ← THE CORE

Walk through the three nested layers:

**Outer loop — price levels (`:32-66`):**
```java
while (incoming.getRemainingQty() > 0) {
    PriceLevel bestLevel = oppositeSide.bestLevel();
    if (bestLevel == null) break;                           // no liquidity

    // Price crossing check (limit orders only)
    if (incoming.getType() == LIMIT && !priceMatches(incoming, bestLevel.getPrice())) {
        break;                                              // price doesn't cross
    }
    ...
}
```

**Middle loop — orders within a level, FIFO (`:42-60`):**
```java
while (!bestLevel.isEmpty() && incoming.getRemainingQty() > 0) {
    Order resting = bestLevel.peekFirst();                  // :43 — FIFO peek
    long fillQty = Math.min(incoming.getRemainingQty(),
                            resting.getRemainingQty());     // :44

    incoming.fill(fillQty);                                 // :47
    resting.fill(fillQty);                                  // :48

    // Trade at resting order's price (passive price improvement)
    trades.add(new Trade(buyId, sellId, resting.getPrice(), fillQty));  // :53

    if (!resting.isActive()) {                              // :56
        bestLevel.removeFirst();                            // :57 — O(1)
        orderIndex.remove(resting.getOrderId());            // :58
    }
}
```

**Cleanup — empty level (`:63-65`):**
```java
if (bestLevel.isEmpty()) {
    oppositeSide.removeLevel(bestLevel.getPrice());
}
```

**Say:** *"Three layers: sweep through price levels, walk each level in FIFO, fill both sides. The fill quantity is always min(incoming remaining, resting remaining) — this handles partial fills naturally. Trade price is the resting order's price — the aggressor gets price improvement if they offered a better price."*

### 6c. priceMatches — crossing check (`:76-80`)

```java
private boolean priceMatches(Order incoming, BigDecimal restingPrice) {
    int cmp = incoming.getPrice().compareTo(restingPrice);
    return incoming.getSide() == Side.BUY ? cmp >= 0 : cmp <= 0;
}
```

**Say:** *"A BUY crosses if its price >= the ask (willing to pay at least that much). A SELL crosses if its price <= the bid (willing to accept at most that much). Market orders skip this check entirely — they match at any price."*

---

## Stop 7 — OrderBook facade

**File:** `book/OrderBook.java`

### 7a. State (`:20-26`)

```java
private final BookSide bids = new BookSide(Side.BUY);
private final BookSide asks = new BookSide(Side.SELL);
private final Map<Long, Order> orderIndex = new HashMap<>();
private final MatchingEngine engine = new MatchingEngine();
```

**Say:** *"Two BookSides, a HashMap index for O(1) cancel, and a stateless matching engine. The orderIndex is the key optimization — without it, cancelling an order would require scanning every price level on both sides."*

### 7b. placeOrder — the complete flow (`:33-56`) ← CORE FLOW

```java
public List<Trade> placeOrder(Order order) {
    BookSide sameSide = (order.getSide() == BUY) ? bids : asks;
    BookSide oppositeSide = (order.getSide() == BUY) ? asks : bids;

    // Step 1: Match against opposite side
    List<Trade> trades = engine.match(order, oppositeSide, orderIndex);

    // Step 2: Rest remaining quantity (limit only)
    if (order.isActive()) {
        if (order.getType() == LIMIT) {
            sameSide.addOrder(order);                       // rest in book
            orderIndex.put(order.getOrderId(), order);      // index for cancel
        } else {
            order.cancel();                                 // market: IOC semantics
        }
    }

    return trades;
}
```

**Draw on whiteboard:**
```
Incoming order
    │
    ▼
[1] engine.match(order, oppositeSide)
    │             │
    │         List<Trade> (fills)
    │
    ▼
[2] order still active?
    ├── YES + LIMIT  → rest in sameSide + index
    ├── YES + MARKET → cancel (IOC — never rests)
    └── NO (fully filled) → nothing to do
```

**Say:** *"Two-step process: match first, then rest. The engine fills what it can, then OrderBook decides what to do with the remainder. Limit orders rest in the book at their price. Market orders are cancelled if not fully filled — they never rest because they have no price."*

### 7c. cancelOrder (`:60-70`)

```java
public void cancelOrder(long orderId) {
    Order order = orderIndex.get(orderId);              // O(1) lookup
    BookSide side = (order.getSide() == BUY) ? bids : asks;
    side.removeOrder(order);                            // O(log P + N)
    orderIndex.remove(orderId);                         // O(1)
    order.cancel();                                     // status → CANCELLED
}
```

**Say:** *"The order index makes cancel O(1) for the lookup. Then we remove from the BookSide (find the price level in TreeMap, scan the LinkedList at that level). Without the index, we'd need to scan all price levels on both sides."*

### 7d. modifyOrder — cancel + replace (`:78-90`)

```java
public List<Trade> modifyOrder(long orderId, BigDecimal newPrice, long newQty) {
    cancelOrder(orderId);                               // cancel existing
    Order replacement = Order.limitOrder(existing.getSide(), newPrice, newQty);
    return placeOrder(replacement);                     // place new
}
```

**Say:** *"Modify = cancel + replace. The new order gets a new ID and goes to the back of the FIFO queue. This intentionally loses time priority — same behavior as NYSE, Nasdaq, and CME. Prevents gaming: you can't place a small order to get early priority, then increase the size."*

---

## Stop 8 — Trace a complete matching scenario

**Use Scenario 2 from `OrderBookApp.java:53-67`**

**Setup:** Best ask is $151.00 with two orders: a1(100 qty) + a4(75 qty). Incoming: BUY LIMIT 120 @ $151.25.

**Step by step through MatchingEngine.match():**

```
OUTER LOOP — iteration 1:
  bestLevel = ask @ $151.00 (orders: [a1(100), a4(75)])
  priceMatches: $151.25 >= $151.00 → YES, prices cross

  INNER LOOP — order a1:
    fillQty = min(120, 100) = 100
    incoming.fill(100) → remaining = 20, status = PARTIALLY_FILLED
    a1.fill(100)       → remaining = 0, status = FILLED
    Trade #1: 100 shares @ $151.00 (resting price, not $151.25!)
    a1 fully filled → removeFirst(), orderIndex.remove(a1)

  INNER LOOP — order a4:
    fillQty = min(20, 75) = 20
    incoming.fill(20)  → remaining = 0, status = FILLED
    a4.fill(20)        → remaining = 55, status = PARTIALLY_FILLED
    Trade #2: 20 shares @ $151.00
    a4 still active → stays in level

  incoming fully filled → exit outer loop

RESULT: 2 trades, incoming FILLED, a4 has 55 remaining at $151.00
```

**Say:** *"Three key things happened: (1) FIFO — a1 was served before a4. (2) Partial fill — a4 got partially filled and stays in the book. (3) Passive price improvement — the buyer offered $151.25 but got filled at $151.00, saving $0.25 per share."*

**Back in OrderBook.placeOrder():**
```
order.isActive()? → NO (FILLED)
→ doesn't rest in book
→ return 2 trades
```

---

## Stop 9 — Extension points (if time allows)

| Extension | What changes | What stays the same |
|-----------|-------------|---------------------|
| Pro-rata matching | New `MatchingEngine` impl | OrderBook, BookSide, PriceLevel, Order |
| IOC/FOK order types | New `OrderType` values + logic in `placeOrder` | MatchingEngine, BookSide |
| Stop orders | New trigger data structure + conversion logic | Core matching algorithm |
| Event sourcing | Event log wrapper around OrderBook operations | All domain classes |
| Thread safety | Single-threaded event loop (LMAX Disruptor) | Core algorithm, just wrap in ring buffer |
| Self-trade prevention | Account/trader field on Order, check in engine | BookSide, PriceLevel |

**Say:** *"The matching engine is the most likely swap point — pro-rata for options, auction for opening/closing. Everything else stays stable. For thread safety, production uses a single-threaded event loop, not locks — the book is always single-threaded, orders arrive via a lock-free ring buffer."*

---

## Cheat Sheet — Complexity

| Operation | Time | Why |
|-----------|------|-----|
| Place (no match) | O(log P) | TreeMap insert |
| Place (with match) | O(log P + M) | M = number of fills across levels |
| Cancel | O(log P + N) | Index O(1), TreeMap O(log P), level scan O(N) |
| Modify | O(log P + N + M) | Cancel + place |
| Best bid/ask | O(1) | `TreeMap.firstEntry()` |
| Spread | O(1) | Two `firstEntry()` calls |
| Depth (top K) | O(K) | Iterate K TreeMap entries |

P = price levels, N = orders at a price level, M = matched orders.

**Production optimization:** Custom doubly-linked list in PriceLevel with node references in the order index. Cancel drops from O(N) to O(1). This is the #1 optimization interviewers ask about.

---

## Suggested Interview Pacing (35 min)

| Time | Stop | What to cover |
|------|------|---------------|
| 0-3 min | Overview | Draw the book structure, explain bid/ask sides, spread |
| 3-4 min | Stop 1 | Enums — OrderStatus state machine |
| 4-7 min | Stop 2 | Order — factory methods, fill(), BigDecimal justification |
| 7-8 min | Stop 3 | Trade — immutable record, 30 seconds |
| 8-10 min | Stop 4 | PriceLevel — LinkedList FIFO, O(1) operations |
| 10-14 min | Stop 5 | **BookSide** — TreeMap + Comparator strategy, computeIfAbsent |
| 14-22 min | Stop 6 | **MatchingEngine** — the matching loop, priceMatches, trade pricing |
| 22-26 min | Stop 7 | OrderBook facade — placeOrder flow, cancel, modify |
| 26-33 min | Stop 8 | Trace Scenario 2 on whiteboard (crossing limit fills 2 orders) |
| 33-35 min | Stop 9 | Extension points + handle follow-up questions |
