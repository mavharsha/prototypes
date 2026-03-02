package com.elevator.system;

import com.elevator.exception.ElevatorNotFoundException;
import com.elevator.exception.InvalidFloorException;
import com.elevator.model.Direction;
import com.elevator.model.ElevatorState;
import com.elevator.model.Request;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central elevator system facade.
 *
 * Manages a bank of elevators, dispatches hall calls,
 * and simulates movement via step().
 */
public class ElevatorSystem {

    private final int minFloor;
    private final int maxFloor;
    private final List<Elevator> elevators;
    private final Dispatcher dispatcher = new Dispatcher();

    public ElevatorSystem(int numElevators, int minFloor, int maxFloor) {
        if (numElevators <= 0) throw new IllegalArgumentException("Need at least 1 elevator");
        if (minFloor >= maxFloor) throw new IllegalArgumentException("minFloor must be < maxFloor");

        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.elevators = new ArrayList<>();
        for (int i = 1; i <= numElevators; i++) {
            elevators.add(new Elevator(i, minFloor, maxFloor));
        }
    }

    // ── Hall Call (external request) ────────────────────────────

    /**
     * Person at a floor presses UP or DOWN.
     * Dispatcher assigns the best elevator.
     *
     * @return the assigned elevator's ID
     */
    public int requestElevator(int floor, Direction direction) {
        validateFloor(floor);
        if (direction == Direction.IDLE) {
            throw new InvalidFloorException("Hall call direction cannot be IDLE");
        }

        Elevator assigned = dispatcher.dispatch(elevators, floor, direction);
        if (assigned == null) {
            throw new IllegalStateException("No elevators available (all in maintenance)");
        }

        assigned.addHallCall(floor, direction);
        return assigned.getId();
    }

    // ── Cabin Call (internal request) ───────────────────────────

    /**
     * Person inside elevator presses a floor button.
     */
    public void pressFloor(int elevatorId, int targetFloor) {
        validateFloor(targetFloor);
        Elevator elevator = getElevator(elevatorId);
        elevator.addStop(targetFloor);
    }

    // ── Simulation ──────────────────────────────────────────────

    /**
     * Advance all elevators by one floor.
     * Returns a log of events (which elevators stopped where).
     */
    public List<String> step() {
        List<String> events = new ArrayList<>();
        for (Elevator e : elevators) {
            if (e.getState() == ElevatorState.MOVING) {
                boolean stopped = e.step();
                if (stopped) {
                    events.add(String.format("Elevator %d stopped at floor %d",
                            e.getId(), e.getCurrentFloor()));
                }
            }
        }
        return events;
    }

    /**
     * Run steps until all elevators are idle.
     * Returns a full log of events.
     */
    public List<String> runToCompletion() {
        List<String> allEvents = new ArrayList<>();
        int stepCount = 0;
        int maxSteps = (maxFloor - minFloor) * 4; // safety limit

        while (hasMovingElevators() && stepCount < maxSteps) {
            stepCount++;
            List<String> events = step();
            for (String event : events) {
                allEvents.add(String.format("[Step %d] %s", stepCount, event));
            }
        }
        return allEvents;
    }

    // ── Maintenance ─────────────────────────────────────────────

    public void setMaintenance(int elevatorId, boolean maintenance) {
        getElevator(elevatorId).setMaintenance(maintenance);
    }

    // ── Queries ─────────────────────────────────────────────────

    public Elevator getElevator(int elevatorId) {
        if (elevatorId < 1 || elevatorId > elevators.size()) {
            throw new ElevatorNotFoundException(elevatorId);
        }
        return elevators.get(elevatorId - 1);
    }

    public boolean hasMovingElevators() {
        return elevators.stream().anyMatch(e -> e.getState() == ElevatorState.MOVING);
    }

    public List<Elevator> getElevators() {
        return Collections.unmodifiableList(elevators);
    }

    // ── Display ─────────────────────────────────────────────────

    public void printStatus() {
        System.out.println("\n═══════════════════════════════════════════");
        System.out.printf("  Elevator System: Floors %d–%d%n", minFloor, maxFloor);
        System.out.println("═══════════════════════════════════════════");

        for (Elevator e : elevators) {
            System.out.printf("  [%d] Floor %-2d | %-7s | %-11s | stops↑=%s ↓=%s%n",
                    e.getId(), e.getCurrentFloor(),
                    e.getDirection(), e.getState(),
                    e.getUpStops(), e.getDownStops());
        }

        System.out.println("═══════════════════════════════════════════\n");
    }

    // ── Internal ────────────────────────────────────────────────

    private void validateFloor(int floor) {
        if (floor < minFloor || floor > maxFloor) {
            throw new InvalidFloorException(floor, minFloor, maxFloor);
        }
    }
}
