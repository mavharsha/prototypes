package com.elevator.system;

import com.elevator.exception.InvalidFloorException;
import com.elevator.model.Direction;
import com.elevator.model.ElevatorState;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * A single elevator using the LOOK algorithm (elevator algorithm).
 *
 * Continues in the current direction, serving requests along the way.
 * Reverses when there are no more requests in the current direction.
 *
 * Uses two TreeSets:
 *   - upStops: floors to visit while going UP (natural order)
 *   - downStops: floors to visit while going DOWN (reverse order)
 */
public class Elevator {

    private final int id;
    private final int minFloor;
    private final int maxFloor;

    private int currentFloor;
    private Direction direction;
    private ElevatorState state;

    private final TreeSet<Integer> upStops;    // natural order — serve lowest first
    private final TreeSet<Integer> downStops;  // natural order — we use descendingIterator

    public Elevator(int id, int minFloor, int maxFloor) {
        this.id = id;
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.currentFloor = minFloor;
        this.direction = Direction.IDLE;
        this.state = ElevatorState.IDLE;
        this.upStops = new TreeSet<>();
        this.downStops = new TreeSet<>();
    }

    // ── Add Destination ─────────────────────────────────────────

    /**
     * Add a floor to visit. Automatically assigns to the correct set
     * based on current floor and direction.
     */
    public void addStop(int floor) {
        validateFloor(floor);
        if (floor == currentFloor) return; // already here

        if (floor > currentFloor) {
            upStops.add(floor);
        } else {
            downStops.add(floor);
        }

        // If idle, start moving towards the requested floor
        if (state == ElevatorState.IDLE) {
            direction = (floor > currentFloor) ? Direction.UP : Direction.DOWN;
            state = ElevatorState.MOVING;
        }
    }

    /**
     * Add a stop for a hall call with a specific direction.
     * If the elevator is moving away from the floor, the stop goes
     * into the opposite set to be served on the return trip.
     */
    public void addHallCall(int floor, Direction requestedDirection) {
        validateFloor(floor);
        if (floor == currentFloor && direction == Direction.IDLE) {
            // Already here and idle — just set direction
            direction = requestedDirection;
            return;
        }

        if (floor > currentFloor) {
            upStops.add(floor);
        } else if (floor < currentFloor) {
            downStops.add(floor);
        } else {
            // On the same floor but moving — add to the requested direction's set
            if (requestedDirection == Direction.UP) {
                upStops.add(floor);
            } else {
                downStops.add(floor);
            }
        }

        if (state == ElevatorState.IDLE) {
            direction = (floor > currentFloor) ? Direction.UP :
                        (floor < currentFloor) ? Direction.DOWN : requestedDirection;
            state = ElevatorState.MOVING;
        }
    }

    // ── Step (simulate one floor of movement) ───────────────────

    /**
     * Move the elevator one floor in its current direction.
     * Returns true if the elevator stopped at a floor (to pick up / drop off).
     */
    public boolean step() {
        if (state != ElevatorState.MOVING) return false;

        // Move one floor
        if (direction == Direction.UP) {
            currentFloor++;
        } else if (direction == Direction.DOWN) {
            currentFloor--;
        }

        // Check if we stop at this floor
        boolean stopped = false;
        if (direction == Direction.UP && upStops.remove(currentFloor)) {
            stopped = true;
        } else if (direction == Direction.DOWN && downStops.remove(currentFloor)) {
            stopped = true;
        }

        // Decide next direction (LOOK algorithm)
        if (direction == Direction.UP && upStops.isEmpty()) {
            if (!downStops.isEmpty()) {
                direction = Direction.DOWN;
            } else {
                direction = Direction.IDLE;
                state = ElevatorState.IDLE;
            }
        } else if (direction == Direction.DOWN && downStops.isEmpty()) {
            if (!upStops.isEmpty()) {
                direction = Direction.UP;
            } else {
                direction = Direction.IDLE;
                state = ElevatorState.IDLE;
            }
        }

        return stopped;
    }

    // ── Queries ─────────────────────────────────────────────────

    public int pendingStops() {
        return upStops.size() + downStops.size();
    }

    public boolean isIdle() {
        return state == ElevatorState.IDLE;
    }

    /**
     * Estimated distance to reach a floor.
     * For LOOK scheduling: direct distance if on the way, otherwise full sweep distance.
     */
    public int distanceTo(int floor, Direction requestedDirection) {
        if (state == ElevatorState.MAINTENANCE) return Integer.MAX_VALUE;

        if (state == ElevatorState.IDLE) {
            return Math.abs(currentFloor - floor);
        }

        // Moving towards the floor in the same direction
        if (direction == Direction.UP && floor >= currentFloor && requestedDirection == Direction.UP) {
            return floor - currentFloor;
        }
        if (direction == Direction.DOWN && floor <= currentFloor && requestedDirection == Direction.DOWN) {
            return currentFloor - floor;
        }

        // Need to reverse — estimate full sweep distance
        if (direction == Direction.UP) {
            int topmost = upStops.isEmpty() ? currentFloor : upStops.last();
            return (topmost - currentFloor) + (topmost - floor);
        } else {
            int bottommost = downStops.isEmpty() ? currentFloor : downStops.first();
            return (currentFloor - bottommost) + (floor - bottommost);
        }
    }

    public void setMaintenance(boolean maintenance) {
        if (maintenance) {
            this.state = ElevatorState.MAINTENANCE;
            this.direction = Direction.IDLE;
            upStops.clear();
            downStops.clear();
        } else {
            this.state = ElevatorState.IDLE;
        }
    }

    // ── Getters ─────────────────────────────────────────────────

    public int getId()               { return id; }
    public int getCurrentFloor()     { return currentFloor; }
    public Direction getDirection()   { return direction; }
    public ElevatorState getState()  { return state; }
    public Set<Integer> getUpStops()   { return Collections.unmodifiableSet(upStops); }
    public Set<Integer> getDownStops() { return Collections.unmodifiableSet(downStops); }

    @Override
    public String toString() {
        return String.format("Elevator %d: floor=%d, dir=%s, state=%s, stops↑=%s, stops↓=%s",
                id, currentFloor, direction, state, upStops, downStops);
    }

    // ── Internal ────────────────────────────────────────────────

    private void validateFloor(int floor) {
        if (floor < minFloor || floor > maxFloor) {
            throw new InvalidFloorException(floor, minFloor, maxFloor);
        }
    }
}
