package com.parkinglot.model;

import com.parkinglot.exception.SpotNotAvailableException;

import java.util.concurrent.atomic.AtomicLong;

public class ParkingSpot {

    private static final AtomicLong ID_GEN = new AtomicLong(1);

    private final long spotId;
    private final SpotType type;
    private final int floor;
    private Vehicle parkedVehicle;

    public ParkingSpot(SpotType type, int floor) {
        this.spotId = ID_GEN.getAndIncrement();
        this.type = type;
        this.floor = floor;
        this.parkedVehicle = null;
    }

    // ── Operations ──────────────────────────────────────────────

    public boolean canFit(VehicleType vehicleType) {
        return isAvailable() && type.canFit(vehicleType);
    }

    public void park(Vehicle vehicle) {
        if (!isAvailable()) {
            throw new SpotNotAvailableException(spotId);
        }
        if (!type.canFit(vehicle.getType())) {
            throw new SpotNotAvailableException(
                    String.format("%s cannot fit in %s spot", vehicle.getType(), type));
        }
        this.parkedVehicle = vehicle;
    }

    public Vehicle unpark() {
        if (isAvailable()) {
            throw new SpotNotAvailableException("Spot " + spotId + " is already empty");
        }
        Vehicle vehicle = this.parkedVehicle;
        this.parkedVehicle = null;
        return vehicle;
    }

    // ── Queries ─────────────────────────────────────────────────

    public boolean isAvailable() {
        return parkedVehicle == null;
    }

    // ── Getters ─────────────────────────────────────────────────

    public long getSpotId()          { return spotId; }
    public SpotType getType()        { return type; }
    public int getFloor()            { return floor; }
    public Vehicle getParkedVehicle() { return parkedVehicle; }

    @Override
    public String toString() {
        return String.format("Spot{id=%d, %s, floor=%d, %s}",
                spotId, type, floor,
                isAvailable() ? "AVAILABLE" : "OCCUPIED by " + parkedVehicle);
    }
}
