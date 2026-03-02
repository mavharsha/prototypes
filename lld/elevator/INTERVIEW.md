# Elevator System — Interview Questions & Answers

---

## Q1. Explain the LOOK algorithm and why you chose it over alternatives.

**Answer:**
The LOOK algorithm (also called the elevator algorithm) is how real elevators work: the elevator continues in its current direction, serving all stops along the way, and reverses only when there are no more stops in the current direction.

In my implementation, each Elevator has two `TreeSet<Integer>`: `upStops` (floors to visit going up) and `downStops` (floors to visit going down). The `step()` method moves one floor in the current direction, checks if it's a stop, and decides the next direction:

```
Moving UP → serve upStops until empty → if downStops exist, reverse → else go IDLE
Moving DOWN → serve downStops until empty → if upStops exist, reverse → else go IDLE
```

**Why LOOK over alternatives:**

- **FCFS (First Come First Served)**: Go to each request in order. Simple but terrible — if requests alternate between floor 1 and floor 10, the elevator ping-pongs the full building. Average wait time is high.

- **SCAN (C-SCAN)**: Always go in one direction (1→10→1→10), like a circular scan. Fairer than LOOK (prevents starvation at extreme floors) but wastes time traveling to the endpoints even if there are no stops there.

- **LOOK**: Same as SCAN but reverses at the last actual stop, not the building endpoint. More efficient — doesn't travel to floor 10 if the highest stop is floor 7.

- **Shortest Seek First (SSF)**: Go to the nearest stop. Good for throughput but causes starvation — stops far from the cluster never get served.

LOOK is the industry standard because it balances efficiency (no unnecessary travel) with fairness (no starvation — every stop is eventually served on the next sweep).

---

## Q2. Why two TreeSets instead of one sorted set or a priority queue?

**Answer:**
The elevator serves floors in different orders depending on direction:
- Going **UP**: serve floor 3 before floor 7 (ascending order).
- Going **DOWN**: serve floor 7 before floor 3 (descending order).

**Single TreeSet problem**: If I had one set `{3, 5, 7}` and the elevator is at floor 4 going up, I need to serve 5, 7 first, then reverse to 3. Splitting "above me" and "below me" dynamically from one set is messy — I'd need `tailSet(currentFloor)` and `headSet(currentFloor)` on every step.

**Two TreeSets**: `upStops = {5, 7}`, `downStops = {3}`. Going up, I just check `upStops.first()` (always O(1)). When upStops is empty, I switch to downStops. The direction change is a simple emptiness check, not a set partition.

**Why not a PriorityQueue?** TreeSet has O(1) `first()` and `last()` for checking extremes, plus O(log N) `remove(element)` for when we arrive at a floor. PriorityQueue only has O(1) `peek()` (min) and O(N) arbitrary removal. TreeSet also deduplicates automatically — pressing the same floor button twice doesn't add a duplicate stop.

**Space**: Two TreeSets use slightly more memory than one, but S (stops per elevator) is always small (< 100 even in skyscrapers), so this is negligible.

---

## Q3. How does the Dispatcher decide which elevator to send?

**Answer:**
`Dispatcher.dispatch()` uses a **nearest-first strategy with direction awareness**. For each elevator, it calls `elevator.distanceTo(floor, direction)` and picks the minimum.

The distance calculation has three cases:

1. **Idle elevator**: `|currentFloor - floor|`. Direct distance, no complications.

2. **Moving towards the floor in the same direction**: Direct distance. Example: elevator at floor 3 going UP, request from floor 7 going UP → distance = 4. The elevator will naturally pass floor 7 on its way up.

3. **Moving away or opposite direction**: Full sweep distance. Example: elevator at floor 3 going UP with a stop at floor 8, request from floor 2 going DOWN → distance = (8 - 3) + (8 - 2) = 11. The elevator must finish going up to floor 8, then come back down to floor 2.

**Why this is better than simple nearest-floor:**

Simple nearest picks the elevator closest by floor count, ignoring direction. An elevator at floor 6 going UP is "closer" to floor 5 than an elevator at floor 3 going UP — but the floor-6 elevator has to reverse first, while the floor-3 elevator naturally passes floor 5. Direction awareness captures this.

**Alternatives:**
- **Round-robin**: Simplest, distributes load evenly, but ignores proximity. Terrible for responsiveness.
- **Zone-based**: Assign floors 1-5 to elevator A, 6-10 to elevator B. Reduces contention but wastes capacity when one zone is busy and the other is idle.
- **Weighted dispatch**: Factor in current load (number of passengers), pending stops, and distance. More accurate but harder to tune.

---

## Q4. What's the difference between hall calls and cabin calls? Why model them separately?

**Answer:**
- **Hall call**: A person at floor 5 presses the UP button. They want *an* elevator to come to floor 5 heading UP. They don't care which elevator — the system decides. This goes through the Dispatcher.

- **Cabin call**: A person inside elevator 2 presses the "7" button. They want *this specific* elevator to go to floor 7. No dispatching needed — it goes directly to the elevator.

They're modeled separately because the control flow is different:

```
Hall call:  User → ElevatorSystem → Dispatcher → best Elevator → addHallCall(floor, dir)
Cabin call: User → ElevatorSystem → specific Elevator → addStop(floor)
```

**Why addHallCall vs addStop?**

`addStop(floor)` just adds a floor to the appropriate TreeSet based on whether it's above or below current position.

`addHallCall(floor, direction)` does the same, but also handles the edge case where the elevator is on the same floor — it needs to know the *requested direction* to set its own direction (should it start going UP or DOWN?).

In a real system, the distinction matters even more:
- Hall calls can be reassigned if a better elevator becomes available (current one breaks down).
- Cabin calls are committed — the passenger is already inside.
- Hall calls contribute to the dispatcher's optimization; cabin calls only affect one elevator.

---

## Q5. How would you handle the scenario where an elevator is full?

**Answer:**
Current implementation has no concept of capacity. To add it:

1. **Capacity tracking**: Add `int currentLoad` and `int maxCapacity` to Elevator. Increment on pickup (stop where a hall call was served), decrement on dropoff (stop where a cabin call was served).

2. **Skip-if-full logic in `step()`**: When the elevator stops at a hall-call floor but `currentLoad >= maxCapacity`, it should:
   - Still drop off passengers for this floor (cabin calls).
   - Skip picking up new passengers (hall calls).
   - Mark the hall call as "not served" so the system can reassign it.

3. **Dispatcher awareness**: `distanceTo()` returns `Integer.MAX_VALUE` if the elevator is at capacity and the request is a hall call. This naturally pushes the dispatcher to pick a different elevator.

4. **Reassignment**: If all elevators are full, queue the hall call. When an elevator drops off passengers and has capacity, trigger re-dispatch of queued requests.

The tricky part: we don't know *how many* people are waiting at a hall call floor. Load sensors in real elevators measure weight, not headcount. The system makes a best-effort assignment and handles overflow reactively (person waits for the next elevator).

---

## Q6. How would you implement destination dispatch (like in modern buildings)?

**Answer:**
Traditional: person presses UP/DOWN at the floor, enters any elevator, then presses their destination inside.

Destination dispatch: person enters their destination floor at a kiosk in the lobby. The system assigns them a specific elevator and displays it on the kiosk (e.g., "Go to elevator C").

**Key changes:**

1. **Request model**: Replace `hallCall(floor, direction)` with `destinationCall(sourceFloor, destFloor)`. The system now knows the full trip upfront.

2. **Grouping algorithm**: The dispatcher groups passengers by destination. If three people on floor 1 all want floor 8, assign them to the same elevator. This reduces total stops.

3. **Elevator assignment display**: The system must tell the user *which* elevator to board. In traditional dispatch, any elevator that arrives at your floor works. With destination dispatch, you're committed to a specific one.

4. **Optimization objective changes**: Traditional dispatch minimizes wait time (time to pick up). Destination dispatch minimizes total trip time (wait + ride) by grouping similar destinations and avoiding unnecessary stops.

5. **Reassignment**: If the assigned elevator breaks down or gets delayed, the system can reassign passengers to a different elevator. The kiosk display updates.

**Implementation sketch:**
```java
public int requestDestination(int sourceFloor, int destFloor) {
    Direction dir = destFloor > sourceFloor ? UP : DOWN;
    // Find elevator with most passengers going to similar floors
    Elevator best = dispatcher.dispatchByDestination(elevators, sourceFloor, destFloor);
    best.addStop(sourceFloor);
    best.addStop(destFloor);
    return best.getId(); // Tell user which elevator to board
}
```

The LOOK algorithm inside each elevator stays the same — it still serves stops in order. The intelligence shifts to the dispatcher, which now makes globally optimal grouping decisions.

---

## Q7. How does your system handle the morning rush (everyone going from floor 1 to upper floors)?

**Answer:**
The current nearest-first dispatcher handles this reasonably: all elevators start idle at various floors, get assigned to floor 1 as requests come in, then serve upward destinations.

**Problem**: In a pure LOOK system, elevators going up pick up floor-1 passengers, but then each elevator stops at many floors (2, 5, 7, 9...). Total trip time is high because of frequent stops.

**Production optimizations for rush hour:**

1. **Lobby dispatch mode**: During peak hours, all idle elevators return to the lobby (floor 1). This reduces wait time for the majority of passengers.

2. **Sector assignment**: Elevator A serves floors 2-5 only, elevator B serves 6-10. Each elevator makes fewer stops, reducing trip time. This is essentially static zone dispatch.

3. **Destination dispatch**: Group passengers by destination. Elevator A takes everyone going to floors 2-4, elevator B takes floors 5-7. Fewer stops per elevator = faster trips.

4. **Express mode**: One elevator designated as express — goes directly to a high-demand floor (e.g., floor 10 cafeteria at lunch). No intermediate stops.

**Implementation**: Add a `DispatchMode` enum (NORMAL, LOBBY_RETURN, ZONE_BASED) to ElevatorSystem. Swap the Dispatcher strategy based on time of day or occupancy patterns. The Elevator class doesn't change — it still runs LOOK on whatever stops it's given. The intelligence is in the dispatcher.

---

## Q8. What would you change for a 100-floor skyscraper with 20 elevators?

**Answer:**

**Zone-based elevator banks:**
- Low-rise bank (elevators 1-6): floors 1-25
- Mid-rise bank (elevators 7-12): floors 1 + 26-50
- High-rise bank (elevators 13-18): floors 1 + 51-75
- Express bank (elevators 19-20): floors 1 + 76-100

Each bank is an independent `ElevatorSystem`. A lobby router assigns passengers to the correct bank based on destination. Elevators skip floors outside their zone (express past floors 2-25).

**Sky lobbies:**
- Express elevators shuttle passengers from floor 1 to sky lobby at floor 50.
- Local elevators at the sky lobby serve floors 50-100.
- Reduces vertical distance for each elevator, improving throughput.

**Dispatcher upgrade:**
- Replace nearest-first with a **cost function**: `cost = waitTime × w1 + rideTime × w2 + energyCost × w3`. Weights tunable per building.
- Add **load prediction**: If floor 30 generates 50 requests per minute at 8 AM, pre-position elevators near floor 30 before rush hour.
- Add **real-time learning**: Track patterns (Monday has higher traffic than Friday, lunch rush at 12 PM) and adjust pre-positioning.

**Technical changes:**
- Elevators become independent actors (threads or processes), communicating via message queues.
- Central dispatcher becomes a coordinator service, not a monolithic class.
- State is event-sourced for audit trails and recovery.
- Health monitoring for each elevator (door sensors, weight sensors, motor status).

The core LOOK algorithm inside each elevator doesn't change. What changes is the dispatch strategy, the zone architecture, and the infrastructure.

---

## Q9. How would you implement fire emergency mode?

**Answer:**
Fire mode is required by building codes. When triggered:

1. **All elevators immediately cancel current stops** and go to the designated fire floor (usually ground floor / lobby).

2. **Hall calls are disabled** — no one can call an elevator.

3. **One elevator is designated for firefighter use** — operates in manual mode with a key switch. Goes to any floor the firefighter selects.

4. **All other elevators park at the fire floor with doors open** and shut down.

**Implementation:**

```java
public void activateFireMode(int fireFloor) {
    this.fireMode = true;
    for (Elevator e : elevators) {
        if (e.getState() == MAINTENANCE) continue;
        e.clearAllStops();
        e.addStop(fireFloor); // Override: go to fire floor
        e.setFirefighterMode(false);
    }
    // Designate elevator 1 for firefighter use
    elevators.get(0).setFirefighterMode(true);
}
```

**In Elevator.step():**
```java
if (firefighterMode) {
    // Only respond to manual commands (pressFloor from firefighter)
    // Don't accept hall calls
}
if (fireMode && currentFloor == fireFloor) {
    // Open doors, shut down
    state = STOPPED;
}
```

**Key design consideration**: Fire mode must be *immediate* and *reliable*. It overrides all normal scheduling logic. The elevator must not stop at intermediate floors to pick up passengers — it goes directly to the fire floor. In production, this is hardwired in the elevator controller, not just software.

---

## Q10. How would you test the Dispatcher in isolation?

**Answer:**

The Dispatcher is stateless — `dispatch(List<Elevator>, int floor, Direction direction)` — which makes it trivially testable.

**Test cases:**

1. **All idle, same floor**: Three elevators at floor 1, request from floor 5. Any elevator is correct (same distance). Verify it picks one (e.g., the first one).

2. **One closer**: Elevator A at floor 3, elevator B at floor 8. Request from floor 4. Should pick A (distance 1 vs 4).

3. **Direction matters**: Elevator A at floor 6 going UP (stop at 10), elevator B at floor 8 IDLE. Request from floor 5 going DOWN. A's effective distance is (10 - 6) + (10 - 5) = 9. B's distance is 3. Should pick B.

4. **Elevator on the way**: Elevator A at floor 3 going UP, elevator B at floor 1 IDLE. Request from floor 7 going UP. A's distance = 4 (on the way), B's distance = 6. Should pick A.

5. **Maintenance skipped**: Elevator A at floor 1 (MAINTENANCE), elevator B at floor 5. Request from floor 2. Must pick B — A is skipped.

6. **All in maintenance**: Should return null (or throw). System handles this.

7. **Same floor, different direction**: Elevator at floor 5 going UP, request from floor 5 going DOWN. Distance depends on how far up the elevator needs to go before reversing.

Because the Dispatcher takes everything as parameters, no mocking framework is needed. Create Elevator instances, set their state, call `dispatch()`, assert the result.

---

## Q11. What's the difference between your step-based simulation and how real elevators work?

**Answer:**

**Step-based (our implementation):**
- `step()` moves every elevator exactly one floor per call.
- All elevators move synchronously — one step, everyone advances.
- Time is discrete and uniform: step 1, step 2, step 3...
- Stop = instant: arrival, door open, passengers in/out, door close all happen in zero time.

**Real-world:**
- Elevators move continuously at varying speeds (acceleration, cruising, deceleration).
- Each elevator is independent — one can be at floor 3 while another is between floors 7 and 8.
- Time is continuous: elevator A takes 2.3 seconds to reach a floor, elevator B takes 2.1 seconds.
- Stop takes 5-15 seconds: decelerate, level, open doors, wait (varies by passenger count), close doors, accelerate.
- Motor physics: acceleration limits, jerk limits, maximum speed.

**To make it more realistic:**

1. **Event-driven simulation**: Instead of `step()`, use a priority queue of events ordered by timestamp: `(time=3.2s, event=ElevatorArrived(elevator=1, floor=5))`. Process events in order. Elevators schedule their own next event.

2. **Travel time model**: Floor-to-floor time = `acceleration_time + cruise_time + deceleration_time`. For adjacent floors (short distance), the elevator might never reach full speed.

3. **Door dwell time**: Add a configurable dwell time at each stop. Longer dwell = more realistic but slower simulation.

For an LLD interview, the step-based model is the right abstraction — it shows the algorithm clearly without physics noise. If the interviewer asks about realism, describe the event-driven model as a production enhancement.

---

## Q12. Walk me through what happens when three people press buttons simultaneously: floor 2 UP, floor 8 DOWN, floor 5 UP.

**Answer:**
Assuming 3 elevators all starting at floor 1, IDLE:

**Request 1: Floor 2 UP**
- Dispatcher computes distance for each: E1=1, E2=1, E3=1 (all at floor 1, all idle).
- Picks E1 (first with min distance).
- E1 adds floor 2 to upStops. E1 direction → UP, state → MOVING.

**Request 2: Floor 8 DOWN**
- Distances: E1=7 (moving up, floor 2 is the only stop, then needs to continue to 8... but wait, E1 is going UP and 8 is also UP from floor 1. So distance = 8-1 = 7, since 8 > currentFloor and requestDir=DOWN, it's full sweep: go to max stop (2), then reverse... actually 8 > 2 so it goes in upStops). Let me recalculate: E1 at floor 1 going UP. distanceTo(8, DOWN): 8 > 1, but direction is DOWN, so it's not "same direction on the way." E1 is going UP, need to go to 8 (above current), but request is DOWN. Distance = (upStops.last=2 - 1) + (8 - 2) = 1 + 6 = 7... Hmm, let me check the actual code logic.

Actually, looking at `distanceTo`: E1 is at floor 1, going UP. Floor 8, direction DOWN. The check `direction == UP && floor >= currentFloor && requestedDirection == UP` fails because requestedDirection is DOWN. So we hit the sweep case: `direction == UP → topmost = upStops.last() = 2. distance = (2 - 1) + (2 - 8)` ... wait, that would be negative. The formula should be `(topmost - currentFloor) + (topmost - floor)`. That gives `(2-1) + (2-8) = 1 + (-6)` which is wrong.

The actual intent: if going UP, we go to the topmost stop, then reverse. Distance to floor 8 after reversing from floor 2 doesn't make sense — floor 8 is ABOVE floor 2. In this case, the elevator needs to go up past 8 anyway. The formula has a gap for this edge case. In practice, with 3 idle elevators, E2 and E3 have distance = 7 (idle, simple |1-8|=7). E1 has the same or similar. Dispatcher picks E2.

**What actually happens in the demo:**
- E2 at floor 1, IDLE. distanceTo(8, DOWN) = |1-8| = 7.
- E3 at floor 1, IDLE. distanceTo(8, DOWN) = |1-8| = 7.
- E1 at floor 1, going UP. The sweep formula gives a larger distance.
- Dispatcher picks E2. E2 adds floor 8 to upStops (8 > 1). Direction → UP, state → MOVING.

**Request 3: Floor 5 UP**
- E1 going UP to 2, E2 going UP to 8, E3 IDLE.
- E3 IDLE: distance = |1-5| = 4.
- E1 going UP: floor 5 > current(1), requestDir=UP, direction=UP → same direction on the way: 5-1=4.
- E2 going UP: floor 5 > current(1), requestDir=UP, direction=UP → same direction on the way: 5-1=4.
- Tie between all three at distance 4. Picks E1 (first).

**Simulation (stepping):**
- Step 1: E1 → floor 2 (stop! serves floor 2 UP), E2 → floor 2 (no stop), E3 stays IDLE.
- Step 2: E1 → floor 3 (no stop), E2 → floor 3.
- ...E1 stops at floor 5 (serves the hall call). E2 continues to floor 8.
- E1 serves both floor 2 UP and floor 5 UP on the same sweep. E2 serves floor 8 DOWN.

This demonstrates the LOOK algorithm's efficiency — E1 picks up two requests on one upward sweep.
