# Parking Garage — LLD Design Notes

## Architecture

```
com.parkinglot
├── model/          Value objects & enums
│   ├── Vehicle          Immutable — license plate + type
│   ├── VehicleType      MOTORCYCLE | CAR | TRUCK
│   ├── ParkingSpot      Mutable — tracks occupancy
│   ├── SpotType         COMPACT | REGULAR | LARGE (with fit rules)
│   ├── ParkingTicket    Mutable — tracks entry/exit/fee
│   └── TicketStatus     ACTIVE | PAID
│
├── garage/         Core logic
│   ├── ParkingGarage    Facade — park, unpark, queries
│   ├── ParkingFloor     Manages spots on a single floor
│   └── FeeCalculator    Stateless — hourly rate by vehicle type
│
└── exception/
    ├── GarageFullException
    ├── InvalidTicketException
    ├── InvalidVehicleException
    ├── SpotNotAvailableException
    └── VehicleNotFoundException
```

## Key Design Decisions

### 1. SpotType Encodes Fit Rules
- `SpotType.canFit(VehicleType)` centralizes which vehicles fit where.
- COMPACT → motorcycle only. REGULAR → motorcycle, car. LARGE → all.
- Avoids scattered if-else chains in ParkingFloor or ParkingGarage.

### 2. Smallest-Spot-First Allocation
- `ParkingFloor.findAvailableSpot()` iterates COMPACT → REGULAR → LARGE.
- A motorcycle gets a compact spot before consuming a regular one.
- This maximizes overall garage utilization.

### 3. Floor-First Then Spot-Type Search
- `ParkingGarage.findSpot()` searches floor 1 first, then floor 2, etc.
- Within each floor, smallest fitting spot type is preferred.
- Simulates real-world behavior: park as close to the entrance as possible.

### 4. Ticket-Based Entry/Exit
- `parkVehicle()` returns a `ParkingTicket` — the receipt of the transaction.
- `unparkVehicle(ticketId)` looks up the ticket, calculates fee, frees the spot.
- Tickets are the audit trail, not the vehicles.

### 5. Vehicle Index for O(1) Lookup
- `Map<String, ParkingTicket> vehicleIndex` keyed by license plate.
- Prevents double-parking the same vehicle.
- Enables `findByLicensePlate()` without scanning all floors.

### 6. Stateless FeeCalculator
- `calculate(ticket, exitTime)` — pure function, no internal state.
- Hourly rates in an EnumMap, 15-minute grace period, round up to next hour.
- Easy to test, easy to swap (flat rate, daily max, weekend rates, etc.).

### 7. Builder Pattern for ParkingGarage
- Garage construction requires floor configuration (spot counts per type).
- Builder provides a fluent API: `.addFloor(1, compact, regular, large)`.
- Private constructor — can't create a misconfigured garage.

### 8. Explicit Entry/Exit Times for Testing
- `parkVehicle(vehicle, entryTime)` and `unparkVehicle(ticketId, exitTime)`.
- Demo scenarios can simulate "2 hours ago" without `Thread.sleep()`.
- Production API uses `Instant.now()` by default.

## Complexity Summary

| Operation             | Time Complexity | Notes                           |
|-----------------------|-----------------|---------------------------------|
| Park vehicle          | O(F × S)        | F floors × S spots per type     |
| Unpark vehicle        | O(1)            | HashMap ticket lookup           |
| Find by license plate | O(1)            | HashMap vehicle index           |
| Total available       | O(F × S)        | Stream over all floors/spots    |
| Is full (vehicle)     | O(F × S)        | Search for any fitting spot     |
| Calculate fee         | O(1)            | Simple arithmetic               |

F = number of floors, S = spots per floor.

## Design Patterns Used

### 1. Factory Method — `Vehicle.car()`, `Vehicle.motorcycle()`, `Vehicle.truck()`
- Private constructor with static factories enforces valid state at creation.
- `ParkingTicket.issue()` similarly controls ticket creation.

### 2. Facade — `ParkingGarage`
- Hides ParkingFloor, FeeCalculator, ticket index, and vehicle index.
- Public API: `parkVehicle`, `unparkVehicle`, `findByLicensePlate`, queries.

### 3. Builder — `ParkingGarage.Builder`
- Fluent floor configuration: `.addFloor(1, 5, 10, 2)`.
- Alternative rejected: constructor with List<FloorConfig> — less readable.

### 4. Stateless Service — `FeeCalculator`
- No fields, no state. Takes ticket + exit time, returns fee.
- Easy to test, easy to swap for different pricing strategies.

### 5. Strategy (via EnumMap) — `SpotType.canFit()` / `FeeCalculator.HOURLY_RATES`
- SpotType encodes allowed vehicle types as a Set inside the enum.
- FeeCalculator stores rates in EnumMap — O(1) lookup, no if-else chain.

### 6. Implicit State Machine — `TicketStatus`
- `ACTIVE → PAID`. Transitions enforced in `ParkingTicket.markPaid()`.
- Cannot pay an already-paid ticket.

---

## SOLID Principles

### S — Single Responsibility
| Class          | Responsibility                         |
|----------------|----------------------------------------|
| Vehicle        | Identify a vehicle (plate + type)      |
| ParkingSpot    | Track occupancy of a single spot       |
| ParkingTicket  | Track a parking session lifecycle      |
| ParkingFloor   | Manage spots on one floor              |
| FeeCalculator  | Compute parking fees                   |
| ParkingGarage  | Orchestrate parking operations         |

### O — Open/Closed
- FeeCalculator can be replaced with a different pricing strategy without modifying ParkingGarage.
- SpotType.canFit() is extensible — add new vehicle/spot types by extending the enums.

### I — Interface Segregation
- ParkingGarage splits mutators (park/unpark) and queries (available/isFull/find).
- FeeCalculator has a single public method.

---

## What's Not Implemented (interview talking points)

### Multi-Entry / Multi-Exit Gates
- Current: single logical entry/exit point.
- Production: multiple gates with rate limiting, each gate has its own ticket dispenser.

### Payment Systems
- Current: fee calculated at exit, payment assumed instant.
- Production: payment terminals, credit card integration, pre-paid passes, monthly subscriptions.

### Reservation System
- No advance booking — first come, first served only.
- Production: reserve a spot for a time window, with cancellation policy.

### Dynamic Pricing
- Current: flat hourly rate by vehicle type.
- Production: surge pricing, daily max caps, weekend/holiday rates, loyalty discounts.

### EV Charging Spots
- No distinction for electric vehicle charging spots.
- Production: SpotType.EV_CHARGING with charging session management.

### Thread Safety
- Current: single-threaded. All collections are unsynchronized.
- Production: concurrent access from multiple gates — use ConcurrentHashMap or event-sourced design.

### Monitoring & Alerts
- No capacity alerts, no analytics.
- Production: publish events for dashboards (occupancy rate, revenue, avg duration).

### Handicap / Priority Spots
- Not modeled — would be another SpotType with allocation priority.
