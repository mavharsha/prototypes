# Order Book — LLD Design Notes

## Architecture

```
com.orderbook
├── model/          Value objects & enums
│   ├── Order           Immutable identity, mutable fill state
│   ├── Trade           Immutable record of a matched fill
│   ├── Side            BUY | SELL
│   ├── OrderType       LIMIT | MARKET
│   └── OrderStatus     NEW → PARTIALLY_FILLED → FILLED | CANCELLED
│
├── book/           Core data structures
│   ├── OrderBook       Facade — owns both sides, order index, delegates matching
│   ├── BookSide        One side of the book (TreeMap<Price, PriceLevel>)
│   └── PriceLevel      FIFO queue (LinkedList) at a single price point
│
├── engine/         Matching logic
│   └── MatchingEngine  Stateless — receives order + opposite side, returns trades
│
└── exception/
    ├── InvalidOrderException
    └── OrderNotFoundException
```

## Key Design Decisions

### 1. Price-Time Priority (FIFO)
- PriceLevel is a LinkedList — addLast / peekFirst gives O(1) FIFO ordering.
- Orders at the same price fill in arrival order.
- This is the standard matching algorithm used by NYSE, Nasdaq, CME.

### 2. TreeMap for Price Levels
- BUY side: `Comparator.reverseOrder()` → `firstEntry()` = highest bid.
- SELL side: `Comparator.naturalOrder()` → `firstEntry()` = lowest ask.
- O(log P) insert/remove where P = number of distinct price levels.

### 3. BigDecimal for Prices
- Avoids floating-point rounding (e.g. 0.1 + 0.2 != 0.3).
- Industry standard for financial math.
- Alternative: use `long` cents/ticks for speed — but BigDecimal is clearer for an interview.

### 4. Stateless Matching Engine
- `MatchingEngine.match()` takes the incoming order, opposite BookSide, and the order index.
- No internal state — easy to test, reason about, and swap out.
- OrderBook is the coordinator: it decides where to rest, when to cancel, etc.

### 5. HashMap Order Index
- `Map<Long, Order> orderIndex` inside OrderBook gives O(1) cancel/modify lookups.
- Orders are indexed on placement, removed on fill or cancel.
- Without this, cancel-by-id would require scanning all price levels.

### 6. Modify = Cancel + Replace
- `modifyOrder()` cancels the existing order and places a new one.
- **Intentionally loses time priority** — this is the real-world behavior at major exchanges.
- Alternative: in-place modification (preserves priority if only qty decreases), but adds complexity.

### 7. Market Orders — IOC Semantics
- Market orders fill what they can, then unfilled remainder is cancelled.
- They **never rest** in the book (no price to rest at).
- This prevents stale market orders from sitting in the book indefinitely.

### 8. Trade Price = Resting Order's Price
- The aggressive (incoming) order gets the resting order's price.
- This is **passive price improvement** — the aggressor may get a better price than they asked for.
- Example: BUY limit @ 151.25 fills against resting ASK @ 151.00 → trade at 151.00.

### 9. Factory Methods on Order
- `Order.limitOrder()` and `Order.marketOrder()` enforce invariants at creation.
- Private constructor — can't create an invalid Order.
- Validation: positive price (limit), positive quantity.

### 10. AtomicLong ID Generation
- Both Order and Trade use `AtomicLong` for unique IDs.
- Safe for concurrent ID generation even though the rest of the book isn't thread-safe.
- In production: IDs would come from a sequence generator or gateway.

## Complexity Summary

| Operation        | Time Complexity    | Notes                                  |
|------------------|--------------------|----------------------------------------|
| Place (no match) | O(log P)           | TreeMap insert                         |
| Place (match)    | O(log P + M)       | M = number of fills                    |
| Cancel           | O(log P + N)       | Index lookup O(1), level scan O(N)     |
| Best bid/ask     | O(1)               | TreeMap.firstEntry()                   |
| Spread           | O(1)               | Two firstEntry() calls                 |
| Depth (top K)    | O(K)               | Iterate TreeMap values                 |

P = number of price levels, N = orders at a given price level, M = matched orders.

## Design Patterns Used

### 1. Factory Method — `Order.limitOrder()` / `Order.marketOrder()`
- **What:** Static factory methods with a private constructor — callers cannot `new Order(...)` directly.
- **Why:** Enforces invariants at creation time (positive price, positive quantity). An `Order` object is *always* valid the moment it exists. This is "make illegal states unrepresentable."
- **Alternative rejected:** Builder pattern — overkill for two creation paths with few parameters. KISS wins here.

### 2. Facade — `OrderBook`
- **What:** Single entry point (`placeOrder`, `cancelOrder`, `modifyOrder`, queries) that hides BookSide, PriceLevel, MatchingEngine, and the order index.
- **Why:** Callers don't need to know there are two TreeMaps, a HashMap index, and a separate matching engine. The facade keeps the public API small and intent-revealing.
- **Why not mediator:** There's no bidirectional communication between subsystems — OrderBook just delegates downward, so the simpler facade label fits.

### 3. Strategy (via Comparator) — `BookSide`
- **What:** Buy side uses `Comparator.reverseOrder()` (highest bid first); sell side uses `Comparator.naturalOrder()` (lowest ask first). Same class, different behavior.
- **Why:** Avoids duplicating BookSide into `BidSide` / `AskSide` subclasses. The *only* difference between the two sides is sort direction — a comparator captures that exactly.
- **Why not inheritance:** One axis of variation (sort order) doesn't justify a class hierarchy. A lambda is simpler.

### 4. Domain Wrapper — `PriceLevel`
- **What:** Wraps `LinkedList<Order>` with domain methods (`addOrder`, `peekFirst`, `removeOrder`) and implements `Iterable<Order>`.
- **Why:** Encapsulates FIFO semantics and prevents callers from breaking queue invariants (e.g., inserting in the middle). Also attaches the price context to the queue.
- **Why not raw LinkedList:** Leaks implementation — callers would depend on `LinkedList` API instead of domain intent.

### 5. Stateless Service — `MatchingEngine`
- **What:** `match(order, oppositeSide, orderIndex)` takes everything it needs as parameters. No fields, no state between calls.
- **Why:** Easy to test (pass in mocks), easy to reason about (no hidden state), easy to swap (replace with pro-rata or auction engine). The engine is a pure function over the book.
- **Trade-off:** Passing the order index as a parameter is slightly awkward, but it keeps MatchingEngine fully decoupled from OrderBook.

### 6. Implicit State Machine — `OrderStatus`
- **What:** `NEW → PARTIALLY_FILLED → FILLED | CANCELLED`. Transitions enforced in `Order.fill()` and `Order.cancel()`.
- **Why:** Prevents invalid transitions (e.g., filling a cancelled order). The state machine is implicit (if-checks in methods) rather than a formal FSM framework — sufficient for four states.
- **Why not enum-based FSM framework:** YAGNI. Four states and two transition methods don't warrant a framework.

### 7. Cancel-Replace (Modify = Cancel + New)
- **What:** `modifyOrder()` cancels the existing order and places a brand-new one.
- **Why:** Intentionally loses time priority — this matches real exchange behavior (NYSE, CME). It's also simpler than in-place modification which would need to handle partial fills, price changes crossing the spread, etc.
- **Pattern name:** This is a domain pattern from exchange design, not a GoF pattern.

---

## SOLID Principles

### S — Single Responsibility: Applied
Every class has one reason to change:

| Class           | Responsibility                          |
|-----------------|-----------------------------------------|
| Order           | Model an order's identity and fill state |
| Trade           | Immutable record of a matched fill       |
| BookSide        | Manage one side's price levels (TreeMap) |
| PriceLevel      | FIFO queue at a single price             |
| MatchingEngine  | Price-time matching algorithm            |
| OrderBook       | Orchestrate operations across subsystems |

No class mixes concerns — matching logic isn't in OrderBook, book structure isn't in MatchingEngine, and model classes don't know about the book.

### O — Open/Closed: Partially Applied
- **Applied:** BookSide is open to new sort strategies via `Comparator` without modifying the class.
- **Applied:** MatchingEngine is stateless — a new matching strategy (pro-rata, auction) could be a new class with the same method signature.
- **Gap:** MatchingEngine is instantiated directly (`new MatchingEngine()`) inside OrderBook rather than injected. This means swapping strategies requires editing OrderBook. Acceptable for an interview — in production, inject via constructor.
- **Why the gap is OK:** YAGNI — there's only one matching algorithm. Adding an interface + injection now would be speculative abstraction.

### L — Liskov Substitution: Not Violated
- No inheritance hierarchies exist to violate. Enums are final. Exceptions properly extend `RuntimeException`.
- `PriceLevel implements Iterable<Order>` — any code expecting `Iterable` works correctly.

### I — Interface Segregation: Applied
- PriceLevel exposes only `Iterable<Order>` to consumers — not the full `LinkedList` API.
- MatchingEngine has a single public method (`match`) — clients aren't forced to depend on methods they don't use.
- OrderBook's public API is split into mutators (`placeOrder`, `cancelOrder`, `modifyOrder`) and queries (`getBestBid`, `getSpread`, `getDepth`) — callers use only what they need.

### D — Dependency Inversion: Partially Violated
- **Violation:** OrderBook depends on the concrete `MatchingEngine` class, not an abstraction.
- **Violation:** OrderBook creates `BookSide` instances directly.
- **Why it's acceptable:** There's only one implementation of each. Introducing interfaces for a single impl adds indirection with no benefit (see YAGNI below). If a second matching strategy were needed, *then* extract the interface.
- **Production fix:** Constructor injection — `OrderBook(MatchingEngine engine)` — one-line change when needed.

---

## KISS / DRY / YAGNI

### KISS — Keep It Simple
- **TreeMap over hand-coded skip list / red-black tree.** Java's `TreeMap` gives O(log P) with zero custom data structure code. A custom balanced tree would be faster in theory (cache-tuned) but vastly more code and bugs.
- **LinkedList over ring buffer.** For FIFO at a price level, `LinkedList.addLast` / `removeFirst` is O(1) and trivially correct. A ring buffer (like LMAX Disruptor) is faster but far more complex — unnecessary for correctness-first code.
- **BigDecimal over long-cents.** `BigDecimal` is slower but eliminates an entire class of bugs (rounding, overflow, display conversion). For an interview, clarity beats nanoseconds.
- **Factory methods over Builder.** Order has two creation paths with 2–3 parameters each. A Builder would add a class, validation in `build()`, and optional-field ambiguity — all for no gain.
- **No frameworks.** No Spring, no DI container, no event bus. Plain Java classes with explicit wiring. The code is understandable without knowing any framework.

### DRY — Don't Repeat Yourself
- **Single `BookSide` class for both bids and asks.** The only difference is the comparator — parameterized, not duplicated.
- **Single `PriceLevel` class.** No buy-level / sell-level split.
- **`Order.fill()` centralizes fill logic.** MatchingEngine calls `order.fill(qty)` — it doesn't manually update `filledQty` and `status` itself.
- **`bestPrice()` delegates to `bestLevel()`.** No duplicate TreeMap access logic.

### YAGNI — You Aren't Gonna Need It
Deliberately omitted features that would add complexity without serving the core problem:

| Omitted                     | Why                                                       |
|-----------------------------|-----------------------------------------------------------|
| Observer / event bus        | No consumers exist yet — add when a market data feed is needed |
| MatchingEngine interface    | Only one algorithm — extract when a second one appears     |
| Thread safety               | Interview scope is single-threaded; production uses event loop, not locks |
| Persistence / event sourcing| In-memory is sufficient for demonstrating the algorithm    |
| Tick size / lot size checks | Validation at system boundary, not core matching logic     |
| Order ID as UUID            | AtomicLong is simpler and sufficient; UUIDs solve distributed problems we don't have |

**The general principle:** every omission is documented in "What's Not Implemented" below, with a note on *how* to add it. This shows awareness without premature complexity.

---

## What's Not Implemented (interview talking points)

### Thread Safety
- Current: single-threaded only. OrderBook, BookSide, PriceLevel are unsynchronized.
- Production approach: **single-threaded event loop** (LMAX Disruptor pattern).
  - One thread owns the book — no locks needed.
  - Orders arrive via a lock-free ring buffer.
  - This is how real exchanges work (Aeron, LMAX, etc.).
- Alternative: `ReadWriteLock` on OrderBook, but contention kills latency.

### Additional Order Types
- **IOC (Immediate or Cancel)**: like market but with a price limit.
- **FOK (Fill or Kill)**: fill entirely or cancel — no partial fills.
- **Stop orders**: triggered when price crosses a threshold, converted to market/limit.
- **GTC vs Day**: time-in-force — current limit orders are implicitly GTC.

### Self-Trade Prevention
- No concept of traders/accounts currently.
- Production systems check if both sides of a potential trade belong to the same firm.
- Strategies: cancel newest, cancel oldest, cancel both.

### Observability / Events
- No event listeners — in production you'd emit events for:
  - Order placed/filled/cancelled
  - Trade executed
  - Book level updated (market data feed)
- Could use observer pattern or publish to a message bus.

### Validation Gaps
- No tick size enforcement (minimum price increment, e.g., $0.01).
- No lot size enforcement (minimum quantity increment).
- No max order size / circuit breakers.
- No duplicate order detection.

### Persistence
- Purely in-memory — no recovery after restart.
- Production: write-ahead log or event sourcing for replay.

### Build System
- No pom.xml / build.gradle — this is a plain Java source tree.
- To compile: `javac -d out $(find src -name "*.java") && java -cp out com.orderbook.OrderBookApp`
