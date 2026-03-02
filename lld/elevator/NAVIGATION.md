# Elevator System — Interview Navigation Flow

> Step-by-step guide for walking an interviewer through the codebase.
> Each stop references exact files and lines. Spend ~2 min per stop.

---

## Overview (draw on whiteboard first)

```
                     ┌──────────────────────────────┐
                     │       ElevatorSystem          │  ← Facade
                     │    (requestElevator,          │
                     │     pressFloor, step)         │
                     └──────┬───────────┬────────────┘
                            │           │
                 ┌──────────▼──┐   ┌────▼──────────┐
                 │  Dispatcher  │   │   Elevator    │  × N
                 │  (stateless) │   │  (LOOK algo)  │
                 └──────────────┘   └───────────────┘
                                     ┌──────┴──────┐
                                     │  upStops    │  TreeSet<Integer>
                                     │  downStops  │  TreeSet<Integer>
                                     └─────────────┘

   Request types:
     Hall call  ──→  Dispatcher picks best elevator  ──→  elevator.addHallCall()
     Cabin call ──→  Goes directly to elevator       ──→  elevator.addStop()
```

**Say:** *"Three elevators, 10 floors. Two request types — hall calls from floors, cabin calls from inside. Dispatcher assigns hall calls; LOOK algorithm moves each elevator."*

---

## Stop 1 — Enums (30 sec)

**Files:**
- `model/Direction.java:3-7` — `UP | DOWN | IDLE`
- `model/ElevatorState.java:3-7` — `MOVING | IDLE | MAINTENANCE`

**Say:** *"Direction tracks where an elevator is heading — IDLE means no pending work. ElevatorState separates movement from availability — MAINTENANCE means the dispatcher should skip it entirely."*

**Why two concepts?** Direction is about intent (where am I going next). State is about availability (can I accept work).

---

## Stop 2 — Request model

**File:** `model/Request.java`

**Key lines:**
- `:17` — AtomicLong ID generation
- `:21` — `direction` field: meaningful for hall calls, IDLE for cabin calls
- `:34-38` — `hallCall(floor, direction)` factory — validates direction != IDLE
- `:42-44` — `cabinCall(targetFloor)` factory — direction is IDLE (no dispatch needed)
- `:53-55` — `isHallCall()` — simple check: `direction != IDLE`

**Say:** *"Two factory methods unify both request types into one class. The direction field is the discriminator — IDLE means cabin call, UP/DOWN means hall call. Private constructor enforces this."*

**Interviewer might ask:** Why not two separate classes?
**Answer:** They share the same fields (floor + timestamp). The only difference is whether direction is present. One class with a discriminator is simpler than a class hierarchy.

---

## Stop 3 — Elevator (the core — spend the most time here)

**File:** `system/Elevator.java`

### 3a. State and data structures (`:21-43`)

```
Fields:
  id, minFloor, maxFloor          ← immutable config
  currentFloor, direction, state  ← mutable position
  upStops (TreeSet)               ← floors to serve going UP
  downStops (TreeSet)             ← floors to serve going DOWN
```

**Say:** *"Two TreeSets are the key insight. upStops holds floors above me, downStops holds floors below me. TreeSet gives O(log N) insert, O(1) first/last, and auto-deduplication — pressing floor 7 twice adds only one stop."*

**Why not one sorted set?** You'd need to partition it on every step (which stops are above me? below me?). Two sets make the direction switch a simple emptiness check.

### 3b. addStop — cabin call path (`:51-66`)

```
addStop(floor):
  if floor > currentFloor → upStops.add(floor)
  if floor < currentFloor → downStops.add(floor)
  if IDLE → set direction + state = MOVING
```

**Say:** *"Simple: floor goes into the set matching its direction from current position. If idle, the elevator wakes up and starts moving towards it."*

### 3c. addHallCall — hall call path (`:73-99`)

**Say:** *"Same as addStop, but handles one extra edge case: elevator is at the same floor as the request. For addStop that's a no-op (already here). For a hall call, the caller wants to go in a specific direction, so we set that direction even though we don't move."*

Walk through the three branches:
- `:81-82` — floor above → upStops (same as addStop)
- `:83-84` — floor below → downStops (same as addStop)
- `:85-91` — same floor, moving → add to the requested direction's set (will serve on next sweep)

### 3d. step() — LOOK algorithm (`:107-143`) ← THE CORE

```
step():
  1. Move one floor in current direction          ← :111-115
  2. Check if this floor is a stop, remove it     ← :118-123
  3. Decide next direction:                       ← :126-140
     - Current set empty + other set has work → REVERSE
     - Both sets empty → IDLE
     - Current set not empty → KEEP GOING
```

**Walk through line by line:**

**`:111-115` — Move:**
```java
if (direction == UP)   currentFloor++;
if (direction == DOWN) currentFloor--;
```

**`:118-123` — Check stop:**
```java
if (UP   && upStops.remove(currentFloor))   → stopped = true
if (DOWN && downStops.remove(currentFloor)) → stopped = true
```
*"TreeSet.remove returns true if the element was present. One call does lookup + removal."*

**`:126-140` — Direction decision (LOOK):**
```java
if (UP && upStops.isEmpty()):
    downStops not empty → direction = DOWN    // reverse
    downStops empty     → IDLE                // done

if (DOWN && downStops.isEmpty()):
    upStops not empty   → direction = UP      // reverse
    upStops empty       → IDLE                // done
```

**Say:** *"This is the LOOK algorithm. Keep going until you run out of stops in the current direction, then reverse if there's work the other way, or go idle. It's the same algorithm elevators use in real buildings and disk heads use for I/O scheduling."*

**Why LOOK over SCAN?** SCAN always goes to the physical endpoint (floor 1 or floor 10) before reversing. LOOK reverses at the last actual stop — no wasted travel.

### 3e. distanceTo — how the Dispatcher scores elevators (`:159-182`)

Three cases:

```
MAINTENANCE → Integer.MAX_VALUE (skip me)        ← :160

IDLE → |currentFloor - floor|                    ← :162-164

Same direction, on the way:
  UP   && floor >= current && reqDir == UP  → floor - current    ← :167-168
  DOWN && floor <= current && reqDir == DOWN → current - floor   ← :170-172

Need to reverse (catch-all):
  Going UP   → (topStop - current) + (topStop - floor)          ← :175-177
  Going DOWN → (current - bottomStop) + (floor - bottomStop)    ← :178-181
```

**Say:** *"Idle is simple. On-the-way is direct distance. The interesting case is 'need to reverse' — the elevator has to finish its current sweep, then come back. The full sweep distance is: distance to farthest current stop + distance from that stop back to the requested floor."*

**Draw on whiteboard:**
```
  Elevator at 3 going UP, stop at 8.
  Request from floor 2 going DOWN.

  Path: 3 →→→ 8 (finish UP) →→→ 2 (serve request)
  Distance = (8-3) + (8-2) = 5 + 6 = 11
```

---

## Stop 4 — Dispatcher (`:28-45`)

**File:** `system/Dispatcher.java`

```
dispatch(elevators, floor, direction):
  for each elevator:
    skip if MAINTENANCE
    compute distance = elevator.distanceTo(floor, direction)
    track minimum
  return best
```

**Say:** *"Stateless — takes everything as parameters, returns the best elevator. The intelligence is in distanceTo() on each elevator, not in the dispatcher. This makes it easy to swap strategies: round-robin, zone-based, weighted — just write a new Dispatcher."*

**Total complexity:** O(E) where E = number of elevators. Each `distanceTo` is O(1).

---

## Stop 5 — ElevatorSystem facade

**File:** `system/ElevatorSystem.java`

### 5a. Construction (`:26-36`)
```java
new ElevatorSystem(3, 1, 10)  // 3 elevators, floors 1-10
```
*"Creates N elevators, all starting at minFloor, all IDLE."*

### 5b. Hall call flow (`:46-59`)
```
requestElevator(floor, direction):
  validate floor
  dispatcher.dispatch(elevators, floor, direction) → best elevator
  best.addHallCall(floor, direction)
  return best.getId()
```

**Say:** *"The facade validates, dispatches, and delegates. The caller gets back the elevator ID — in a real building, this would show on the floor display."*

### 5c. Cabin call flow (`:66-70`)
```
pressFloor(elevatorId, targetFloor):
  validate floor
  getElevator(elevatorId).addStop(targetFloor)
```

**Say:** *"No dispatching — the passenger already chose this elevator by being inside it."*

### 5d. Simulation (`:78-109`)
```
step():
  for each MOVING elevator → elevator.step()
  collect events (who stopped where)

runToCompletion():
  repeat step() until all IDLE (with safety limit)
```

**Say:** *"Step advances every elevator by one floor simultaneously. runToCompletion is a convenience for demos — production would be event-driven with real timers."*

---

## Stop 6 — Trace a complete scenario

**Use Scenario 2 from `ElevatorApp.java:50-77`**

**Setup:** After scenario 1, E1 at floor 5 (IDLE), E2 at floor 1 (IDLE), E3 at floor 1 (IDLE).

**Request 1 — Floor 8 DOWN (`:61`):**
```
Dispatcher scores:
  E1: IDLE, |5-8| = 3  ← nearest
  E2: IDLE, |1-8| = 7
  E3: IDLE, |1-8| = 7
→ Assigns E1. E1.addHallCall(8, DOWN). upStops={8}, dir=UP, state=MOVING.
```

**Request 2 — Floor 2 UP (`:65`):**
```
Dispatcher scores:
  E1: MOVING UP, floor 2 < current 5. Need reverse: topmost=8, (8-5)+(8-2)=9
  E2: IDLE, |1-2| = 1  ← nearest
  E3: IDLE, |1-2| = 1  ← tie, picks E2 first
→ Assigns E2. E2.addHallCall(2, UP). upStops={2}, dir=UP, state=MOVING.
```

**Request 3 — Floor 3 UP (`:69`):**
```
Dispatcher scores:
  E1: MOVING UP, floor 3 < 5. Need reverse: (8-5)+(8-3)=8
  E2: MOVING UP, floor 3 >= 1, reqDir=UP, same direction: 3-1=2  ← nearest
  E3: IDLE, |1-3| = 2  ← tie with E2
→ Assigns E2 (first with min). E2 adds 3 to upStops. upStops={2,3}.
```

**Simulation:**
```
Step 1: E1: 5→6         E2: 1→2 (STOP — serves floor 2 UP)
Step 2: E1: 6→7         E2: 2→3 (STOP — serves floor 3 UP, upStops empty → IDLE)
Step 3: E1: 7→8 (STOP — serves floor 8 DOWN, upStops empty → check downStops: empty → IDLE)
```

**Say:** *"E2 picks up both floor 2 and floor 3 on a single upward sweep — the LOOK algorithm naturally batches requests in the same direction. E1 serves floor 8 independently. No elevator wasted a trip."*

---

## Stop 7 — Extension points (if time allows)

Point to these as "production upgrades, same architecture":

| Extension | What changes | What stays the same |
|-----------|-------------|---------------------|
| Destination dispatch | New `Dispatcher` + Request model | Elevator, LOOK algorithm |
| Zone-based banks | Multiple `ElevatorSystem` instances + router | Everything inside each system |
| Capacity limits | `currentLoad` field in Elevator, skip-if-full in step | Dispatcher, ElevatorSystem |
| Fire emergency | `activateFireMode()` in ElevatorSystem, override stops | Elevator movement logic |
| Weight sensors | Feed into `currentLoad`, real data instead of counting | Same as capacity limits |

**Say:** *"The core LOOK algorithm in Elevator never changes. Extensions are either new Dispatchers, new fields on Elevator, or new modes in ElevatorSystem. That's the value of the facade + stateless service separation."*

---

## Cheat Sheet — Complexity

| Operation | Time | Why |
|-----------|------|-----|
| Add stop | O(log S) | TreeSet insert |
| Step (1 elevator) | O(log S) | TreeSet remove |
| Dispatch | O(E) | Scan elevators, O(1) distanceTo each |
| Hall call (end to end) | O(E + log S) | Dispatch + addHallCall |
| Run to completion | O(F × E × log S) | F floors × E elevators |

S = stops/elevator, E = elevators, F = total floors.

---

## Suggested Interview Pacing (35 min)

| Time | Stop | What to cover |
|------|------|---------------|
| 0-3 min | Overview | Draw the architecture diagram, name the classes |
| 3-5 min | Stops 1-2 | Enums + Request model (quick, set the vocabulary) |
| 5-15 min | Stop 3 | **Elevator** — TreeSets, addStop, step(), LOOK algorithm |
| 15-18 min | Stop 4 | Dispatcher — distanceTo scoring, nearest-first |
| 18-22 min | Stop 5 | ElevatorSystem facade — hall call vs cabin call flow |
| 22-30 min | Stop 6 | Trace Scenario 2 end-to-end on whiteboard |
| 30-35 min | Stop 7 | Extension points + handle follow-up questions |
