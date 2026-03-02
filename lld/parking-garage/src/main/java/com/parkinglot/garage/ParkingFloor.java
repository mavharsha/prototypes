package com.parkinglot.garage;

import com.parkinglot.model.ParkingSpot;
import com.parkinglot.model.SpotType;
import com.parkinglot.model.VehicleType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A single floor in the parking garage.
 *
 * Manages a collection of spots organized by type.
 * Uses EnumMap for O(1) lookup of spots by type.
 */
public class ParkingFloor {

    private final int floorNumber;
    private final List<ParkingSpot> spots;
    private final Map<SpotType, List<ParkingSpot>> spotsByType;

    public ParkingFloor(int floorNumber) {
        this.floorNumber = floorNumber;
        this.spots = new ArrayList<>();
        this.spotsByType = new EnumMap<>(SpotType.class);
        for (SpotType type : SpotType.values()) {
            spotsByType.put(type, new ArrayList<>());
        }
    }

    // ── Setup ───────────────────────────────────────────────────

    public void addSpots(SpotType type, int count) {
        for (int i = 0; i < count; i++) {
            ParkingSpot spot = new ParkingSpot(type, floorNumber);
            spots.add(spot);
            spotsByType.get(type).add(spot);
        }
    }

    // ── Spot Finding ────────────────────────────────────────────

    /**
     * Find the first available spot that can fit the given vehicle type.
     * Prefers smaller spots (COMPACT → REGULAR → LARGE) to avoid waste.
     */
    public Optional<ParkingSpot> findAvailableSpot(VehicleType vehicleType) {
        for (SpotType spotType : SpotType.values()) {
            if (!spotType.canFit(vehicleType)) continue;

            Optional<ParkingSpot> spot = spotsByType.get(spotType).stream()
                    .filter(ParkingSpot::isAvailable)
                    .findFirst();
            if (spot.isPresent()) return spot;
        }
        return Optional.empty();
    }

    // ── Queries ─────────────────────────────────────────────────

    public long availableCount() {
        return spots.stream().filter(ParkingSpot::isAvailable).count();
    }

    public long availableCount(SpotType type) {
        return spotsByType.get(type).stream().filter(ParkingSpot::isAvailable).count();
    }

    public long occupiedCount() {
        return spots.size() - availableCount();
    }

    public int totalSpots() {
        return spots.size();
    }

    // ── Getters ─────────────────────────────────────────────────

    public int getFloorNumber()                { return floorNumber; }
    public List<ParkingSpot> getSpots()        { return Collections.unmodifiableList(spots); }

    @Override
    public String toString() {
        return String.format("Floor %d: %d/%d spots occupied",
                floorNumber, occupiedCount(), totalSpots());
    }
}
