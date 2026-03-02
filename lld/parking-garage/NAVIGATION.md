# Parking Garage — Interview Navigation Flow

> Step-by-step guide for walking an interviewer through the codebase.
> Each stop references exact files and lines. Spend ~2 min per stop.

---

## Overview (draw on whiteboard first)

```
                  ┌────────────────────────────────────┐
                  │          ParkingGarage              │  ← Facade
                  │  (parkVehicle, unparkVehicle,       │
                  │   findByLicensePlate, isFull)       │
                  └──┬──────────┬──────────────┬───────┘
                     │          │              │
          ┌──────────▼──┐  ┌───▼────────┐  ┌──▼───────────────┐
          │ ParkingFloor │  │FeeCalc     │  │ Indexes          │
          │  × N floors  │  │(stateless) │  │ activeTickets    │
          └──────┬───────┘  └────────────┘  │ vehicleIndex     │
                 │                          └──────────────────┘
       ┌─────────▼─────────┐
       │ EnumMap<SpotType,  │
       │  List<ParkingSpot>>│
       └───────────────────┘

   Fit rules (encoded in SpotType enum):
     COMPACT → motorcycle only
     REGULAR → motorcycle, car
     LARGE   → motorcycle, car, truck

   Allocation: floor 1 first → smallest fitting spot type first
```

**Say:** *"3-floor garage with three spot types. Vehicles park in the smallest spot that fits. Ticket-based entry/exit, stateless fee calculation. Two HashMaps for O(1) lookup by ticket ID and license plate."*

---

## Stop 1 — Enums and fit rules (1 min)

**Files:**
- `model/VehicleType.java` — `MOTORCYCLE | CAR | TRUCK`
- `model/SpotType.java:5-20` — fit rules encoded in the enum
- `model/TicketStatus.java` — `ACTIVE | PAID`

**Key lines in SpotType:**
```java
COMPACT(Set.of(VehicleType.MOTORCYCLE)),                            // :7
REGULAR(Set.of(VehicleType.MOTORCYCLE, VehicleType.CAR)),           // :8
LARGE(Set.of(VehicleType.MOTORCYCLE, VehicleType.CAR, VehicleType.TRUCK));  // :9

public boolean canFit(VehicleType vehicleType) {                    // :17
    return allowedVehicles.contains(vehicleType);                   // :18
}
```

**Say:** *"Fit rules are encoded inside the SpotType enum, not scattered in if-else chains. Each spot type carries a Set of allowed vehicle types. `canFit()` is a single Set.contains() call — O(1), no branching logic anywhere else."*

**Why this matters:** Adding a new spot type (e.g., EV_CHARGING) is one line in this enum. Nothing else changes.

---

## Stop 2 — Vehicle (immutable value object)

**File:** `model/Vehicle.java`

**Key lines:**
- `:10-13` — Private constructor: `licensePlate` + `type`, both final
- `:17-25` — `of(plate, type)` factory validates both fields
- `:27-37` — Convenience factories: `Vehicle.car("ABC")`, `.motorcycle()`, `.truck()`

**Say:** *"Vehicle is immutable — once created, it never changes. Factory methods enforce valid state: no blank plates, no null types. Convenience factories make the demo readable: `Vehicle.car("ABC-1234")` instead of `Vehicle.of("ABC-1234", VehicleType.CAR)`."*

**Interviewer might ask:** Why not just a record?
**Answer:** A Java record would work well here. We use a class with private constructor to match the orderbook pattern and to keep the factory method validation explicit.

---

## Stop 3 — ParkingSpot (mutable, tracks occupancy)

**File:** `model/ParkingSpot.java`

**Key lines:**
- `:9` — AtomicLong ID generation
- `:14` — `Vehicle parkedVehicle` — null means available
- `:25-27` — `canFit()` — combines availability + fit rule check:
  ```java
  return isAvailable() && type.canFit(vehicleType);
  ```
- `:29-38` — `park(vehicle)` — double-validates: spot is free AND vehicle fits
- `:40-47` — `unpark()` — clears the vehicle reference, returns the vehicle
- `:51-53` — `isAvailable()` — `parkedVehicle == null`

**Say:** *"A spot knows its type and floor, and optionally holds a vehicle. park/unpark are the state transitions. The spot delegates fit-checking to SpotType.canFit() — it doesn't contain vehicle-type-specific logic itself."*

**Draw on whiteboard:**
```
ParkingSpot state machine:

  AVAILABLE ──park(vehicle)──→ OCCUPIED
  OCCUPIED  ──unpark()───────→ AVAILABLE
```

---

## Stop 4 — ParkingTicket (the transaction record)

**File:** `model/ParkingTicket.java`

**Key lines:**
- `:14-20` — Fields: vehicle, spot, entryTime, exitTime (null until paid), fee (null until paid), status
- `:22-30` — Private constructor: starts ACTIVE, no exit time, no fee
- `:34-41` — `issue()` factory: two overloads (now vs explicit time for demos)
- `:45-62` — `markPaid(fee, exitTime)` — state transition ACTIVE → PAID, enforces no double-pay
- `:66-69` — `getDuration()` — uses exitTime if paid, else Instant.now()

**Say:** *"The ticket is the audit trail. It links a vehicle to a spot and tracks the session lifecycle. It starts ACTIVE when issued at entry, becomes PAID when the vehicle leaves. The fee and exit time are null until paid — they're set atomically in markPaid()."*

**State machine:**
```
ACTIVE ──markPaid(fee)──→ PAID
                          (exitTime, fee set)
                          (cannot pay again)
```

**Interviewer might ask:** Why is the ticket mutable instead of creating a new "PaidTicket"?
**Answer:** Simpler. A mutable ticket with a status field avoids a class hierarchy (ActiveTicket vs PaidTicket) for just two states. In production with event sourcing, you'd have immutable events (`TicketIssued`, `TicketPaid`) and project the current state.

---

## Stop 5 — ParkingFloor (spot organization + allocation)

**File:** `garage/ParkingFloor.java`

### 5a. Data structure (`:22-33`)

```java
private final List<ParkingSpot> spots;                              // all spots on this floor
private final Map<SpotType, List<ParkingSpot>> spotsByType;         // EnumMap for O(1) type lookup
```

**Say:** *"Two views of the same data. `spots` is the master list. `spotsByType` is an EnumMap that groups spots by type for fast lookup — when a CAR arrives, we jump directly to the REGULAR list without scanning COMPACT spots."*

### 5b. addSpots (`:37-43`)

```java
public void addSpots(SpotType type, int count) {
    for (int i = 0; i < count; i++) {
        ParkingSpot spot = new ParkingSpot(type, floorNumber);
        spots.add(spot);                    // master list
        spotsByType.get(type).add(spot);    // type-indexed view
    }
}
```

### 5c. findAvailableSpot — the allocation algorithm (`:51-61`) ← IMPORTANT

```java
public Optional<ParkingSpot> findAvailableSpot(VehicleType vehicleType) {
    for (SpotType spotType : SpotType.values()) {           // COMPACT → REGULAR → LARGE
        if (!spotType.canFit(vehicleType)) continue;        // skip types that can't fit

        Optional<ParkingSpot> spot = spotsByType.get(spotType).stream()
                .filter(ParkingSpot::isAvailable)
                .findFirst();
        if (spot.isPresent()) return spot;
    }
    return Optional.empty();
}
```

**Say:** *"This is the key algorithm. We iterate SpotType.values() which goes COMPACT → REGULAR → LARGE — Java enum ordering. For each type, we check canFit, then find the first available spot. A motorcycle tries COMPACT first, a car skips COMPACT (canFit returns false) and starts at REGULAR, a truck jumps straight to LARGE."*

**Why smallest-first?** Maximizes utilization. If motorcycles consume REGULAR spots, cars run out while COMPACT spots sit empty.

**Draw on whiteboard:**
```
MOTORCYCLE: try COMPACT ──→ try REGULAR ──→ try LARGE
CAR:        skip COMPACT ──→ try REGULAR ──→ try LARGE
TRUCK:      skip COMPACT ──→ skip REGULAR ──→ try LARGE
```

---

## Stop 6 — FeeCalculator (stateless service)

**File:** `garage/FeeCalculator.java`

**Key lines:**
- `:21-27` — `HOURLY_RATES` EnumMap: Motorcycle $2, Car $5, Truck $10
- `:29` — `GRACE_PERIOD_MINUTES = 15`
- `:38-52` — `calculate(ticket, exitTime)`:

```java
Duration duration = Duration.between(ticket.getEntryTime(), exitTime);  // :39
long totalMinutes = duration.toMinutes();                                // :40

if (totalMinutes <= GRACE_PERIOD_MINUTES) return BigDecimal.ZERO;       // :43-44 (grace)

long hours = (totalMinutes + 59) / 60;                                  // :48 (round up)

BigDecimal rate = HOURLY_RATES.get(ticket.getVehicle().getType());      // :50
return rate.multiply(BigDecimal.valueOf(hours));                         // :51
```

**Say:** *"Pure function — no internal state. Takes a ticket and exit time, returns a fee. The algorithm: grace period check, then round up to next hour, then multiply by the hourly rate. The +59 trick rounds up without floating-point: 61 minutes → (61+59)/60 = 2 hours."*

**Interviewer might ask:** Why separate from ParkingTicket?
**Answer:** SRP — pricing rules change independently from ticket lifecycle. I can swap in a DynamicFeeCalculator (surge pricing based on occupancy), WeekendFeeCalculator, or SubscriptionFeeCalculator without touching ParkingTicket.

---

## Stop 7 — ParkingGarage facade (spend the most time here)

**File:** `garage/ParkingGarage.java`

### 7a. State (`:24-28`)

```java
private final List<ParkingFloor> floors;
private final Map<Long, ParkingTicket> activeTickets = new HashMap<>();     // ticketId → ticket
private final Map<String, ParkingTicket> vehicleIndex = new HashMap<>();    // plate → ticket
private final FeeCalculator feeCalculator = new FeeCalculator();
```

**Say:** *"Two indexes for O(1) lookup. activeTickets for unparking by ticket ID. vehicleIndex for lookup by license plate and duplicate prevention. Both are kept in sync — entries added on park, removed on unpark."*

### 7b. Builder (`:37-61`)

```java
ParkingGarage.builder("Downtown Garage")
    .addFloor(1, 5, 10, 2)     // floor 1: 5 compact, 10 regular, 2 large
    .addFloor(2, 5, 10, 2)
    .addFloor(3, 0, 5, 5)
    .build();
```

**Say:** *"Builder because construction has variable steps (N floors, each with 3 spot counts). Private constructor on ParkingGarage — can only create via builder. Reads like a specification."*

### 7c. parkVehicle — the park flow (`:77-90`) ← CORE FLOW

```java
public ParkingTicket parkVehicle(Vehicle vehicle, Instant entryTime) {
    // 1. Duplicate check via vehicle index
    if (vehicleIndex.containsKey(vehicle.getLicensePlate())) {      // :78
        throw new InvalidTicketException("already parked");
    }

    // 2. Find spot (floor-first, smallest-type-first)
    ParkingSpot spot = findSpot(vehicle.getType());                 // :82

    // 3. Park vehicle in spot
    spot.park(vehicle);                                             // :83

    // 4. Issue ticket
    ParkingTicket ticket = ParkingTicket.issue(vehicle, spot, entryTime);  // :85

    // 5. Index for O(1) lookup later
    activeTickets.put(ticket.getTicketId(), ticket);                // :86
    vehicleIndex.put(vehicle.getLicensePlate(), ticket);            // :87

    return ticket;                                                  // :89
}
```

**Walk through step by step on whiteboard:**
```
Vehicle arrives
    │
    ▼
[1] vehicleIndex check ──→ already parked? throw
    │
    ▼
[2] findSpot(type) ──→ floors bottom-up, smallest spot type first
    │
    ▼
[3] spot.park(vehicle) ──→ marks spot as OCCUPIED
    │
    ▼
[4] ParkingTicket.issue() ──→ ACTIVE ticket with entry time
    │
    ▼
[5] Index ticket by ID + plate
    │
    ▼
Return ticket to caller
```

### 7d. unparkVehicle — the exit flow (`:105-119`) ← CORE FLOW

```java
public ParkingTicket unparkVehicle(long ticketId, Instant exitTime) {
    // 1. Find active ticket
    ParkingTicket ticket = activeTickets.get(ticketId);              // :106

    // 2. Calculate fee
    BigDecimal fee = feeCalculator.calculate(ticket, exitTime);     // :111

    // 3. Mark ticket paid
    ticket.markPaid(fee, exitTime);                                 // :112

    // 4. Free the spot
    ticket.getSpot().unpark();                                      // :113

    // 5. Remove from both indexes
    activeTickets.remove(ticketId);                                 // :115
    vehicleIndex.remove(ticket.getVehicle().getLicensePlate());     // :116

    return ticket;                                                  // :118
}
```

**Walk through on whiteboard:**
```
Driver pays at exit
    │
    ▼
[1] activeTickets.get(ticketId) ──→ O(1) lookup
    │
    ▼
[2] feeCalculator.calculate(ticket, now) ──→ stateless fee computation
    │
    ▼
[3] ticket.markPaid(fee) ──→ ACTIVE → PAID
    │
    ▼
[4] spot.unpark() ──→ OCCUPIED → AVAILABLE
    │
    ▼
[5] Remove from activeTickets + vehicleIndex
    │
    ▼
Return completed ticket (with fee)
```

### 7e. findSpot — internal allocation (`:185-191`)

```java
private ParkingSpot findSpot(VehicleType vehicleType) {
    for (ParkingFloor floor : floors) {                             // bottom-up
        var spot = floor.findAvailableSpot(vehicleType);            // smallest-first
        if (spot.isPresent()) return spot.get();
    }
    throw new GarageFullException(vehicleType);
}
```

**Say:** *"Two levels of search: outer loop over floors (bottom-up for proximity to entrance), inner loop in ParkingFloor.findAvailableSpot() over spot types (smallest-first for utilization). If nothing found across all floors, throw GarageFullException."*

---

## Stop 8 — Trace a complete scenario

**Use Scenario 2 from `ParkingGarageApp.java:65-88`**

**Setup:** Garage has cars at spots 6,7 (REGULAR), motorcycle at spot 1 (COMPACT), truck at spot 16 (LARGE). All parked 2 hours ago.

**Unpark car ABC-1234:**
```
1. findByLicensePlate("ABC-1234")
   → vehicleIndex.get("ABC-1234") → Ticket 1 (O(1))

2. unparkVehicle(ticketId=1)
   → activeTickets.get(1) → Ticket 1
   → feeCalculator.calculate(ticket, now):
       duration = 120 minutes
       120 > 15 (not grace period)
       hours = (120 + 59) / 60 = 2
       rate = $5.00 (CAR)
       fee = 2 × $5.00 = $10.00
   → ticket.markPaid($10.00) → ACTIVE → PAID
   → spot.unpark() → spot 6 now AVAILABLE
   → remove from both indexes
```

**Grace period case — car parked 10 minutes:**
```
1. parkVehicle(Vehicle.car("SHORT-01"), 10minAgo)
   → findSpot(CAR): floor 1 → REGULAR → spot 6 (just freed!) → park
   → issue ticket, index it

2. unparkVehicle(ticket)
   → duration = 10 minutes
   → 10 ≤ 15 → GRACE PERIOD → fee = $0.00
```

**Say:** *"The grace period demonstrates the FeeCalculator's edge case handling. A 10-minute stay costs nothing — handles the 'just picking someone up' scenario. 16 minutes would round up to 1 hour and cost $5."*

---

## Stop 9 — Extension points (if time allows)

| Extension | What changes | What stays the same |
|-----------|-------------|---------------------|
| EV charging | New `SpotType.EV_CHARGING` in enum | ParkingFloor, ParkingGarage, FeeCalculator |
| Dynamic pricing | New `FeeCalculator` impl (takes occupancy %) | ParkingGarage, ParkingTicket, ParkingSpot |
| Reservations | New `Reservation` model + reservation index | Spot allocation adds reserved-spot exclusion |
| Multi-gate | Per-floor locks or event-sourced design | Core domain model |
| Monthly passes | New `Subscription` model, FeeCalculator checks it | ParkingSpot, ParkingFloor |

**Say:** *"Every extension touches one layer. New spot types → enum. New pricing → FeeCalculator. New features → new model + index in ParkingGarage. The core domain model (Vehicle, Spot, Ticket) is stable."*

---

## Cheat Sheet — Complexity

| Operation | Time | Why |
|-----------|------|-----|
| Park vehicle | O(F × S) | F floors × S spots per type (worst case) |
| Unpark vehicle | O(1) | HashMap lookup by ticket ID |
| Find by plate | O(1) | HashMap lookup by license plate |
| Calculate fee | O(1) | Arithmetic on duration |
| Is full (type) | O(F × S) | Search all floors for fitting spot |
| Total available | O(F × S) | Stream count (could cache for O(1)) |

F = floors, S = spots per type per floor.

**Production optimization:** Replace `List<ParkingSpot>` with `Queue<ParkingSpot>` (free-list) per type per floor. Park = `poll()` O(1), unpark = `offer()` O(1). Drops park from O(F × S) to O(F).

---

## Suggested Interview Pacing (35 min)

| Time | Stop | What to cover |
|------|------|---------------|
| 0-3 min | Overview | Draw the architecture diagram, name the classes |
| 3-4 min | Stop 1 | SpotType fit rules — the single most important enum |
| 4-6 min | Stops 2-3 | Vehicle (immutable) + ParkingSpot (mutable, park/unpark) |
| 6-9 min | Stop 4 | ParkingTicket lifecycle: ACTIVE → PAID |
| 9-14 min | Stop 5 | **ParkingFloor** — EnumMap structure, findAvailableSpot algorithm |
| 14-17 min | Stop 6 | FeeCalculator — grace period, hourly rounding, stateless |
| 17-25 min | Stop 7 | **ParkingGarage facade** — park flow, unpark flow, indexes |
| 25-32 min | Stop 8 | Trace Scenario 2 end-to-end (unpark + grace period) |
| 32-35 min | Stop 9 | Extension points + handle follow-up questions |
