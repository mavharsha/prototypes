package com.elevator.system;

import com.elevator.model.Direction;

import java.util.List;

/**
 * Stateless elevator dispatcher.
 *
 * Assigns hall calls to the best elevator using a nearest-first strategy
 * with direction awareness (prefers elevators already heading towards the caller).
 */
public class Dispatcher {

    /**
     * Find the best elevator for a hall call.
     *
     * Strategy: minimize estimated distance, which accounts for:
     *   - Direct distance if idle
     *   - Direct distance if moving towards the floor in the same direction
     *   - Full sweep distance if moving away (needs to reverse)
     *
     * @param elevators list of all elevators
     * @param floor     the requesting floor
     * @param direction the requested direction (UP or DOWN)
     * @return the best elevator to serve this request
     */
    public Elevator dispatch(List<Elevator> elevators, int floor, Direction direction) {
        Elevator best = null;
        int bestDistance = Integer.MAX_VALUE;

        for (Elevator elevator : elevators) {
            if (elevator.getState() == com.elevator.model.ElevatorState.MAINTENANCE) {
                continue;
            }

            int distance = elevator.distanceTo(floor, direction);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = elevator;
            }
        }

        return best;
    }
}
