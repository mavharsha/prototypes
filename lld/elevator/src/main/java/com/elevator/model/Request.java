package com.elevator.model;

import com.elevator.exception.InvalidFloorException;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A request to the elevator system.
 *
 * Two kinds:
 *   - Hall call: person at a floor presses UP or DOWN → (floor, direction)
 *   - Cabin call: person inside elevator presses a floor button → (elevatorId, targetFloor)
 */
public class Request {

    private static final AtomicLong ID_GEN = new AtomicLong(1);

    private final long requestId;
    private final int floor;
    private final Direction direction; // meaningful for hall calls; IDLE for cabin calls
    private final Instant timestamp;

    private Request(int floor, Direction direction) {
        this.requestId = ID_GEN.getAndIncrement();
        this.floor = floor;
        this.direction = direction;
        this.timestamp = Instant.now();
    }

    // ── Factory methods ────────────────────────────────────────

    /** Hall call: person at a floor wants to go in a direction. */
    public static Request hallCall(int floor, Direction direction) {
        if (direction == Direction.IDLE) {
            throw new InvalidFloorException("Hall call direction cannot be IDLE");
        }
        return new Request(floor, direction);
    }

    /** Cabin call: person inside elevator wants to go to a specific floor. */
    public static Request cabinCall(int targetFloor) {
        return new Request(targetFloor, Direction.IDLE);
    }

    // ── Getters ────────────────────────────────────────────────

    public long getRequestId()     { return requestId; }
    public int getFloor()          { return floor; }
    public Direction getDirection() { return direction; }
    public Instant getTimestamp()   { return timestamp; }

    public boolean isHallCall() {
        return direction != Direction.IDLE;
    }

    @Override
    public String toString() {
        if (isHallCall()) {
            return String.format("HallCall{id=%d, floor=%d, dir=%s}", requestId, floor, direction);
        }
        return String.format("CabinCall{id=%d, floor=%d}", requestId, floor);
    }
}
