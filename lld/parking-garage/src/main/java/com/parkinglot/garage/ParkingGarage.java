package com.parkinglot.garage;

import com.parkinglot.exception.GarageFullException;
import com.parkinglot.exception.InvalidTicketException;
import com.parkinglot.exception.VehicleNotFoundException;
import com.parkinglot.model.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central parking garage facade.
 *
 * Manages floors, active tickets, and vehicle-to-ticket index.
 * Delegates fee calculation to the stateless FeeCalculator.
 */
public class ParkingGarage {

    private final String name;
    private final List<ParkingFloor> floors;
    private final Map<Long, ParkingTicket> activeTickets = new HashMap<>();
    private final Map<String, ParkingTicket> vehicleIndex = new HashMap<>(); // licensePlate → ticket
    private final FeeCalculator feeCalculator = new FeeCalculator();

    private ParkingGarage(String name, List<ParkingFloor> floors) {
        this.name = name;
        this.floors = floors;
    }

    // ── Builder ─────────────────────────────────────────────────

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static class Builder {
        private final String name;
        private final List<ParkingFloor> floors = new ArrayList<>();

        private Builder(String name) {
            this.name = name;
        }

        public Builder addFloor(int floorNumber, int compactSpots, int regularSpots, int largeSpots) {
            ParkingFloor floor = new ParkingFloor(floorNumber);
            floor.addSpots(SpotType.COMPACT, compactSpots);
            floor.addSpots(SpotType.REGULAR, regularSpots);
            floor.addSpots(SpotType.LARGE, largeSpots);
            floors.add(floor);
            return this;
        }

        public ParkingGarage build() {
            return new ParkingGarage(name, new ArrayList<>(floors));
        }
    }

    // ── Park ────────────────────────────────────────────────────

    /**
     * Park a vehicle in the garage.
     * Searches floors bottom-up for the smallest available spot that fits.
     *
     * @return parking ticket for the assigned spot
     * @throws GarageFullException if no spot is available
     */
    public ParkingTicket parkVehicle(Vehicle vehicle) {
        return parkVehicle(vehicle, Instant.now());
    }

    /** Park with explicit entry time — useful for demo scenarios. */
    public ParkingTicket parkVehicle(Vehicle vehicle, Instant entryTime) {
        if (vehicleIndex.containsKey(vehicle.getLicensePlate())) {
            throw new InvalidTicketException("Vehicle " + vehicle.getLicensePlate() + " is already parked");
        }

        ParkingSpot spot = findSpot(vehicle.getType());
        spot.park(vehicle);

        ParkingTicket ticket = ParkingTicket.issue(vehicle, spot, entryTime);
        activeTickets.put(ticket.getTicketId(), ticket);
        vehicleIndex.put(vehicle.getLicensePlate(), ticket);

        return ticket;
    }

    // ── Unpark ──────────────────────────────────────────────────

    /**
     * Unpark a vehicle using its ticket ID.
     * Calculates fee, marks ticket as paid, frees the spot.
     *
     * @return the completed ticket with fee
     */
    public ParkingTicket unparkVehicle(long ticketId) {
        return unparkVehicle(ticketId, Instant.now());
    }

    /** Unpark with explicit exit time — useful for demo scenarios. */
    public ParkingTicket unparkVehicle(long ticketId, Instant exitTime) {
        ParkingTicket ticket = activeTickets.get(ticketId);
        if (ticket == null) {
            throw new InvalidTicketException("Ticket " + ticketId + " not found or already paid");
        }

        BigDecimal fee = feeCalculator.calculate(ticket, exitTime);
        ticket.markPaid(fee, exitTime);
        ticket.getSpot().unpark();

        activeTickets.remove(ticketId);
        vehicleIndex.remove(ticket.getVehicle().getLicensePlate());

        return ticket;
    }

    // ── Queries ─────────────────────────────────────────────────

    public ParkingTicket findByLicensePlate(String licensePlate) {
        ParkingTicket ticket = vehicleIndex.get(licensePlate);
        if (ticket == null) {
            throw new VehicleNotFoundException(licensePlate);
        }
        return ticket;
    }

    public long totalCapacity() {
        return floors.stream().mapToInt(ParkingFloor::totalSpots).sum();
    }

    public long totalAvailable() {
        return floors.stream().mapToLong(ParkingFloor::availableCount).sum();
    }

    public long totalOccupied() {
        return totalCapacity() - totalAvailable();
    }

    public boolean isFull() {
        return totalAvailable() == 0;
    }

    public boolean isFull(VehicleType vehicleType) {
        return floors.stream()
                .noneMatch(floor -> floor.findAvailableSpot(vehicleType).isPresent());
    }

    public int activeTicketCount() {
        return activeTickets.size();
    }

    // ── Getters ─────────────────────────────────────────────────

    public String getName()                       { return name; }
    public List<ParkingFloor> getFloors()          { return Collections.unmodifiableList(floors); }

    // ── Display ─────────────────────────────────────────────────

    public void printStatus() {
        System.out.println("\n═══════════════════════════════════════════");
        System.out.printf("  Parking Garage: %s%n", name);
        System.out.println("═══════════════════════════════════════════");

        for (ParkingFloor floor : floors) {
            System.out.printf("  Floor %d:  %d/%d occupied  [C:%d  R:%d  L:%d available]%n",
                    floor.getFloorNumber(),
                    floor.occupiedCount(), floor.totalSpots(),
                    floor.availableCount(SpotType.COMPACT),
                    floor.availableCount(SpotType.REGULAR),
                    floor.availableCount(SpotType.LARGE));
        }

        System.out.println("  ─────────────────────────────────────────");
        System.out.printf("  Total: %d/%d occupied  |  Active tickets: %d%n",
                totalOccupied(), totalCapacity(), activeTicketCount());
        System.out.println("═══════════════════════════════════════════\n");
    }

    // ── Internal ────────────────────────────────────────────────

    private ParkingSpot findSpot(VehicleType vehicleType) {
        for (ParkingFloor floor : floors) {
            var spot = floor.findAvailableSpot(vehicleType);
            if (spot.isPresent()) return spot.get();
        }
        throw new GarageFullException(vehicleType);
    }
}
