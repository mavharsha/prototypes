# Parking Garage — Interview Questions & Answers

---

## Q1. Walk me through your class hierarchy. What are the core entities and how do they relate?

**Answer:**
There are three core entities forming a chain: **Vehicle → ParkingSpot → ParkingTicket**.

- **Vehicle** is immutable — just a license plate and a type (CAR, MOTORCYCLE, TRUCK). It's a value object that identifies what's being parked. Factory methods (`Vehicle.car("ABC-123")`) enforce valid state.

- **ParkingSpot** is mutable — it has a type (COMPACT, REGULAR, LARGE) and tracks whether a vehicle is parked in it. The fit rules are encoded in `SpotType.canFit(VehicleType)`: compact fits motorcycles only, regular fits motorcycles and cars, large fits everything. The spot doesn't decide allocation — it just knows whether it *can* hold a vehicle.

- **ParkingTicket** is the transaction record — it links a vehicle to a spot, records entry/exit time, and tracks fee/status (ACTIVE → PAID). It's the primary entity for the park/unpark workflow.

Above these, **ParkingFloor** manages a collection of spots (grouped by type in an EnumMap for fast lookup), and **ParkingGarage** is the facade that orchestrates everything — it owns floors, a ticket index, and a vehicle index.

The key insight: tickets are the audit trail, not the vehicles. You unpark by ticket ID, not by license plate (though we support lookup by plate too).

---

## Q2. How does spot allocation work? Why smallest-first?

**Answer:**
When a vehicle arrives, `ParkingGarage.findSpot()` searches floors bottom-up (closest to entrance). On each floor, `ParkingFloor.findAvailableSpot()` iterates spot types in order: COMPACT → REGULAR → LARGE, finding the first available spot of each type that can fit the vehicle.

So a motorcycle gets a compact spot before a regular one, and a car gets a regular spot before a large one. Only trucks go directly to large.

**Why smallest-first:** It maximizes utilization. If motorcycles consumed regular spots freely, we'd run out of regular spots for cars while compact spots sit empty. Smallest-first ensures each vehicle uses the minimum resources.

**Real-world parallel:** Airport parking garages do this — "compact car" sections exist specifically to prevent small cars from consuming full-size spots.

**Alternative strategies:**
- **Nearest-to-elevator**: number spots by distance to the elevator, pick the closest available. Adds a priority field to ParkingSpot.
- **Load-balanced across floors**: distribute evenly to avoid one floor filling first. Useful for multi-exit garages.
- **Reserved spots**: handicap, EV charging, VIP — these get checked first for matching vehicle types.

All of these would be implemented by swapping the allocation logic in `findAvailableSpot()` — the rest of the system stays unchanged.

---

## Q3. Why did you use a separate FeeCalculator instead of putting pricing logic in ParkingTicket?

**Answer:**
Single Responsibility — ParkingTicket tracks the lifecycle of a parking session (entry, exit, status), while FeeCalculator encodes the pricing strategy. Separating them gives:

1. **Swappable pricing**: I can create a `WeekendFeeCalculator`, `SurgePricingCalculator`, or `SubscriptionFeeCalculator` without touching ParkingTicket. In production, pricing rules change frequently — they shouldn't require changes to the ticket model.

2. **Testability**: I can test pricing logic in isolation with synthetic tickets and exact timestamps. No need to mock time or create full garage instances.

3. **Open/Closed**: New pricing strategies don't modify existing classes — they're new implementations.

The FeeCalculator is stateless — `calculate(ticket, exitTime)` is a pure function. It uses an EnumMap for hourly rates (O(1) lookup), a 15-minute grace period, and rounds up to the next hour. All pricing rules are in one place.

If the interviewer asks about dynamic pricing: a DynamicFeeCalculator could take occupancy rate as an additional parameter. At 90%+ occupancy, multiply the rate. The interface stays the same: `calculate(ticket, exitTime)`.

---

## Q4. How do you prevent the same vehicle from parking twice?

**Answer:**
The `vehicleIndex` — a `Map<String, ParkingTicket>` keyed by license plate. In `parkVehicle()`:

```java
if (vehicleIndex.containsKey(vehicle.getLicensePlate())) {
    throw new InvalidTicketException("Vehicle already parked");
}
```

On park: add to the index. On unpark: remove from the index. This gives O(1) duplicate detection.

Without this index, we'd have to scan every spot on every floor to check if the vehicle is already parked — O(F × S).

The vehicle index serves a dual purpose:
1. **Duplicate prevention** at park time.
2. **Lookup by plate** — `findByLicensePlate("ABC-123")` returns the active ticket in O(1). Useful for scenarios like "I lost my ticket, here's my plate number."

---

## Q5. What data structures back the garage, and what are the complexity trade-offs?

**Answer:**

| Structure | Purpose | Complexity |
|-----------|---------|------------|
| `List<ParkingFloor>` | Ordered floors (bottom-up search) | O(F) iteration |
| `EnumMap<SpotType, List<ParkingSpot>>` per floor | Spots grouped by type | O(1) type lookup, O(S) spot scan |
| `HashMap<Long, ParkingTicket>` (activeTickets) | Ticket lookup by ID | O(1) |
| `HashMap<String, ParkingTicket>` (vehicleIndex) | Ticket lookup by plate | O(1) |

**Parking** is O(F × S) worst case — scan floors then spots. But in practice it's much faster: we stop at the first available spot, and EnumMap groups spots by type so we only scan the relevant type.

**Unparking** is O(1) — HashMap lookup by ticket ID.

**Could we make parking O(1)?** Yes, with a free-list per spot type per floor. Maintain a `Queue<ParkingSpot>` of available spots. `poll()` gives an available spot in O(1), `offer()` returns it on unpark. The trade-off: more memory and complexity for setup, but O(1) allocation. Worth it at scale (10K+ spots).

---

## Q6. How would you handle concurrent access from multiple entry/exit gates?

**Answer:**
Current implementation is single-threaded — fine for an interview but not for production with multiple physical gates.

**Option 1: Synchronized facade**
```java
public synchronized ParkingTicket parkVehicle(Vehicle v) { ... }
public synchronized ParkingTicket unparkVehicle(long id) { ... }
```
Simple but creates a bottleneck — only one gate can park/unpark at a time.

**Option 2: ConcurrentHashMap + per-floor locks**
- Replace `activeTickets` and `vehicleIndex` with `ConcurrentHashMap`.
- Add a `ReentrantLock` per `ParkingFloor` — gates locking different floors don't block each other.
- Park: lock floor, find spot, allocate, unlock. Unpark: lock floor, free spot, unlock.
- This gives floor-level parallelism — useful since most contention is on lower floors.

**Option 3: Event-sourced / message queue**
- Each gate publishes `ParkRequest` / `UnparkRequest` events to a queue.
- A single processing thread drains the queue and updates state.
- Same pattern as the order book — single-threaded event loop, no locks.
- Gates get back an async response (ticket or error).

Option 2 is the practical production choice for a parking garage. Option 3 is overkill unless you need perfect audit trails and replayability.

---

## Q7. How would you implement a reservation system on top of this?

**Answer:**
A reservation is a "future claim on a spot." Key additions:

1. **Reservation model**: `Reservation(id, vehicleType, spotType, startTime, endTime, status)`. Status: PENDING → CONFIRMED → CANCELLED / EXPIRED / USED.

2. **Spot inventory split**: Each spot has two states — physically available and reserved. A reserved-but-not-yet-arrived spot is physically empty but logically taken.

3. **Reservation index**: `Map<Long, Reservation>` for O(1) lookup. Also a time-indexed structure (e.g., `TreeMap<Instant, List<Reservation>>`) for checking conflicts.

4. **Conflict detection**: When creating a reservation, check if the requested time window overlaps with existing reservations for the same spot type. This is an interval overlap query.

5. **Arrival**: When the vehicle arrives, match it against the reservation. Convert the reservation to a ticket. If no-show after grace period, release the spot and optionally charge a fee.

6. **Modified allocation**: `findAvailableSpot()` must now exclude reserved spots. `availableCount()` must subtract reserved spots.

The key design tension: do you reserve a *specific spot* or a *spot type on a floor*? Specific spot is simpler but less flexible (what if it's blocked?). Spot type is more flexible but requires a counting-based system rather than spot-level tracking.

---

## Q8. How would you add EV charging spots?

**Answer:**
Three changes:

1. **SpotType enum**: Add `EV_CHARGING` with `canFit(Set.of(VehicleType.CAR))` (or all types if trucks can charge too).

2. **ParkingFloor.addSpots()**: Already works — `floor.addSpots(SpotType.EV_CHARGING, 4)`.

3. **Allocation priority**: EV vehicles should prefer EV_CHARGING spots, but non-EV vehicles should avoid them. Two approaches:
   - **Strict**: EV_CHARGING only for EVs. Add `Vehicle.isElectric` flag. `SpotType.canFit()` checks it.
   - **Soft**: Non-EVs can use EV spots if nothing else is available. `findAvailableSpot()` tries non-EV spots first, falls back to EV spots.

4. **Charging session management** (if needed): `ChargingSession(spotId, startTime, kWhDelivered, rate)`. Integrates with FeeCalculator for combined parking + charging fees.

The architecture supports this cleanly because SpotType and its fit rules are extensible. Adding a new enum value and its allowed vehicles is a one-line change. The garage, floor, and allocation code work without modification.

---

## Q9. Walk me through the fee calculation. What edge cases did you handle?

**Answer:**
`FeeCalculator.calculate(ticket, exitTime)`:

1. **Duration**: `Duration.between(entryTime, exitTime)` in minutes.
2. **Grace period**: If total minutes ≤ 15, fee is $0. Handles the "just picking someone up" case.
3. **Hourly rounding**: Round up to the next hour — `(totalMinutes + 59) / 60`. So 61 minutes = 2 hours, 120 minutes = 2 hours.
4. **Rate lookup**: EnumMap by VehicleType — Motorcycle $2/hr, Car $5/hr, Truck $10/hr.
5. **Final fee**: `hours × rate`, scaled to 2 decimal places.

**Edge cases handled:**
- Zero duration (instant exit): falls in grace period → $0.
- Exactly 15 minutes: grace period → $0.
- 16 minutes: rounds up to 1 hour → full hourly rate.
- Exactly 1 hour: 1 × rate.
- 1 hour 1 minute: rounds up to 2 hours → 2 × rate.

**Edge cases NOT handled (interview talking points):**
- Daily maximum cap (e.g., max $40/day for a car).
- Multi-day stays (different day vs night rates).
- Weekend/holiday rates.
- Pre-paid / subscription discounts.
- Lost ticket (charge maximum daily rate).

All of these would be new FeeCalculator implementations — the interface stays the same.

---

## Q10. Why did you use a Builder for ParkingGarage but Factory Methods for Vehicle and ParkingTicket?

**Answer:**
It depends on construction complexity:

**Vehicle**: Two parameters (plate, type) with two creation paths (car vs motorcycle vs truck). Factory methods are perfect — `Vehicle.car("ABC-123")` is readable, enforces invariants, and doesn't need a builder's fluency.

**ParkingTicket**: Three parameters (vehicle, spot, entryTime) with one creation path. `ParkingTicket.issue(vehicle, spot)` is clear enough. A builder would add ceremony for no benefit.

**ParkingGarage**: Variable number of floors, each with three spot-count parameters. Without a builder, construction looks like:
```java
List<FloorConfig> floors = new ArrayList<>();
floors.add(new FloorConfig(1, 5, 10, 2));
floors.add(new FloorConfig(2, 5, 10, 2));
new ParkingGarage("Downtown", floors);
```

With a builder:
```java
ParkingGarage.builder("Downtown")
    .addFloor(1, 5, 10, 2)
    .addFloor(2, 5, 10, 2)
    .build();
```

The builder is justified here because: (a) construction has multiple steps, (b) the order of steps matters conceptually (floors are searched bottom-up), and (c) the fluent API reads like a specification.

**General rule**: Factory Method for 1-3 fixed parameters, Builder for variable/complex construction. Don't use a builder just because you can.

---

## Q11. How would you handle a multi-story garage with entry on each floor (not just ground floor)?

**Answer:**
Current design searches floors bottom-up, assuming entry is at floor 1. For multi-entry:

1. **Entry floor parameter**: `parkVehicle(vehicle, entryFloor)`. Search starts at `entryFloor` and expands outward (floor 3 entry → check 3, then 2 and 4, then 1 and 5, etc.).

2. **Modified search in ParkingGarage**:
```java
private ParkingSpot findSpot(VehicleType type, int entryFloor) {
    // Expand outward from entry floor
    for (int offset = 0; offset < floors.size(); offset++) {
        for (int dir : new int[]{0, -1, 1}) {
            int idx = entryFloor - 1 + (offset * dir);
            if (idx >= 0 && idx < floors.size()) {
                var spot = floors.get(idx).findAvailableSpot(type);
                if (spot.isPresent()) return spot.get();
            }
        }
    }
    throw new GarageFullException(type);
}
```

3. **Gate tracking**: Each entry gate knows its floor. The gate passes its floor to `parkVehicle()`.

This is a real-world requirement — airport parking garages have entry ramps on multiple levels. The allocation strategy directly affects how far a driver has to walk from their car to the terminal.

---

## Q12. If you had to scale this to handle 100,000 spots across 50 floors, what would you change?

**Answer:**

**Data structure changes:**
- Replace `List<ParkingSpot>` with a **free-list** (`Queue<ParkingSpot>`) per spot type per floor. Parking goes from O(S) scan to O(1) poll. This is the single biggest win.
- Pre-compute availability counts instead of streaming over all spots. Maintain `int availableCount` per type per floor, increment/decrement on park/unpark.

**Architecture changes:**
- **Sharding**: Partition by floor range. Floors 1-10 on shard 1, 11-20 on shard 2, etc. Each shard is an independent ParkingGarage instance. A router picks the shard based on entry point.
- **Event sourcing**: Append park/unpark events to a log. Enables audit trails, analytics, and crash recovery.
- **Caching**: Cache availability counts. Most queries are "is there a spot?" — don't recount every time.

**Operational changes:**
- **Monitoring**: Real-time dashboards for occupancy per floor, revenue, average duration.
- **Capacity alerts**: Trigger when 90%+ full, notify operators and digital signage.
- **Analytics**: Peak hours, average stay duration, revenue per spot type — feed into pricing optimization.

The core domain model (Vehicle, Spot, Ticket) doesn't change. The changes are all in the infrastructure layer — data structures, persistence, and distribution.
