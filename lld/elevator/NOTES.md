# Elevator System — LLD Design Notes

## Architecture

```
com.elevator
├── model/          Value objects & enums
│   ├── Request          Hall call (floor + direction) or cabin call (target floor)
│   ├── Direction        UP | DOWN | IDLE
│   └── ElevatorState    MOVING | IDLE | MAINTENANCE
│
├── system/         Core logic
│   ├── ElevatorSystem   Facade — request elevator, press floor, step simulation
│   ├── Elevator         Single elevator — LOOK algorithm, two TreeSets for stops
│   └── Dispatcher       Stateless — assigns hall calls to nearest suitable elevator
│
└── exception/
    ├── InvalidFloorException
    └── ElevatorNotFoundException
```

## Key Design Decisions

### 1. LOOK Algorithm (Elevator Algorithm)
- Each elevator continues in its current direction, serving stops along the way.
- Reverses only when no more stops remain in the current direction.
- This is the real-world algorithm used by most elevator systems.
- Named after the disk scheduling algorithm with the same behavior.

### 2. Two TreeSets for Stop Management
- `upStops`: TreeSet with natural order — serves lowest floor first while going up.
- `downStops`: TreeSet with natural order — we read via `first()` (lowest) when descending.
- `addStop(floor)`: assigns to upStops if floor > current, downStops if floor < current.
- TreeSet gives O(log N) insert, O(log N) remove, O(1) first/last — ideal for ordered stop sets.

### 3. Hall Calls vs Cabin Calls
- **Hall call**: person at a floor presses UP or DOWN → `(floor, direction)`.
  - Dispatcher assigns the best elevator.
  - Elevator adds the floor to its stop set.
- **Cabin call**: person inside elevator presses a floor → `(elevatorId, targetFloor)`.
  - Goes directly to the specified elevator.
  - No dispatching needed.
- Both ultimately call `elevator.addStop()`.

### 4. Nearest-First Dispatch with Direction Awareness
- `Dispatcher.dispatch()` picks the elevator with minimum estimated distance.
- Distance calculation in `Elevator.distanceTo(floor, direction)`:
  - **Idle**: simple `|currentFloor - floor|`.
  - **Same direction, on the way**: direct distance (floor - current or current - floor).
  - **Need to reverse**: full sweep distance (go to extreme, then come back).
- This avoids starvation — elevators already heading towards a floor are preferred.

### 5. Step-Based Simulation
- `step()` advances each moving elevator by one floor.
- Returns events (which elevators stopped where).
- `runToCompletion()` runs steps until all elevators are idle — useful for demos.
- Alternative: event-driven simulation with timestamps — more realistic but harder to follow.

### 6. Stateless Dispatcher
- `dispatch(elevators, floor, direction)` — pure function, no internal state.
- Easy to test, easy to swap (round-robin, zone-based, load-balanced).
- Dispatcher doesn't remember past assignments.

### 7. Maintenance Mode
- `setMaintenance(true)` takes an elevator offline — clears all stops, sets IDLE direction.
- Dispatcher skips elevators in MAINTENANCE state.
- `setMaintenance(false)` brings it back as IDLE.

## Complexity Summary

| Operation            | Time Complexity | Notes                              |
|----------------------|-----------------|------------------------------------|
| Add stop             | O(log S)        | TreeSet insertion                  |
| Step (one elevator)  | O(log S)        | TreeSet remove at current floor    |
| Dispatch             | O(E)            | Scan all elevators for nearest     |
| Distance estimate    | O(1)            | Arithmetic on current + TreeSet extremes |
| Request elevator     | O(E + log S)    | Dispatch + add stop                |
| Run to completion    | O(F × E × log S)| At most F floors × E elevators     |

E = number of elevators, S = stops per elevator, F = total floors.

## Design Patterns Used

### 1. Factory Method — `Request.hallCall()`, `Request.cabinCall()`
- Two creation paths with different semantics.
- Private constructor, AtomicLong ID generation.

### 2. Facade — `ElevatorSystem`
- Hides Elevator internals, Dispatcher, and floor validation.
- Public API: `requestElevator`, `pressFloor`, `step`, `runToCompletion`, `printStatus`.

### 3. Stateless Service — `Dispatcher`
- No fields. Takes elevators + request parameters, returns best elevator.
- Easy to swap: round-robin, zone-based, weighted, etc.

### 4. Strategy (via `distanceTo`) — `Elevator`
- Each elevator computes its own distance estimate.
- Dispatcher selects the minimum — the elevator "bids" on the request.
- Adding a new dispatch strategy is a new Dispatcher, not changes to Elevator.

### 5. Simulation Pattern — `step()` / `runToCompletion()`
- Discrete event simulation via step function.
- Each step is deterministic — same inputs produce same outputs.
- `runToCompletion()` is a convenience wrapper with a safety limit.

---

## SOLID Principles

### S — Single Responsibility
| Class           | Responsibility                              |
|-----------------|---------------------------------------------|
| Request         | Model a hall call or cabin call              |
| Elevator        | Manage one elevator's stops and movement     |
| Dispatcher      | Assign hall calls to the best elevator       |
| ElevatorSystem  | Orchestrate multiple elevators               |

### O — Open/Closed
- Dispatcher can be replaced without modifying ElevatorSystem or Elevator.
- New elevator algorithms (e.g., destination dispatch) can be a new Elevator subclass.

### I — Interface Segregation
- ElevatorSystem splits: hall calls, cabin calls, simulation, queries.
- Dispatcher has a single public method.

---

## What's Not Implemented (interview talking points)

### Destination Dispatch
- Current: traditional hall call (UP/DOWN at a floor) — don't know destination until inside.
- Modern systems: enter destination floor at the lobby → system groups passengers.
- More efficient but different data model (Request needs destination).

### Weight/Capacity Limits
- No concept of passenger count or weight.
- Production: load sensors, capacity limits, skip-if-full logic.

### Door Open/Close Timing
- Current: stop = instant pickup/dropoff.
- Production: door open timer, door close button, obstruction sensor.

### Priority / Express Mode
- No VIP floors, no fire mode, no emergency override.
- Production: fire service mode (all elevators to ground floor), priority floors.

### Zone-Based Dispatch
- Current: any elevator serves any floor.
- Tall buildings: elevators assigned to floor ranges (low-rise, mid-rise, high-rise).

### Thread Safety
- Current: single-threaded simulation.
- Production: elevator controllers are independent threads communicating via message queues.

### Real-Time Scheduling
- Current: step-based simulation.
- Production: event-driven with real timers, sensor inputs, and motor control.

### Multiple Banks
- Current: single bank of elevators.
- Production: separate banks for different floor ranges, freight elevators.
