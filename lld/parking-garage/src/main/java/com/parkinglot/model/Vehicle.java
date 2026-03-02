package com.parkinglot.model;

import com.parkinglot.exception.InvalidVehicleException;

public class Vehicle {

    private final String licensePlate;
    private final VehicleType type;

    private Vehicle(String licensePlate, VehicleType type) {
        this.licensePlate = licensePlate;
        this.type = type;
    }

    // ── Factory methods ────────────────────────────────────────

    public static Vehicle of(String licensePlate, VehicleType type) {
        if (licensePlate == null || licensePlate.isBlank()) {
            throw new InvalidVehicleException("License plate cannot be blank");
        }
        if (type == null) {
            throw new InvalidVehicleException("Vehicle type is required");
        }
        return new Vehicle(licensePlate, type);
    }

    public static Vehicle car(String licensePlate) {
        return of(licensePlate, VehicleType.CAR);
    }

    public static Vehicle motorcycle(String licensePlate) {
        return of(licensePlate, VehicleType.MOTORCYCLE);
    }

    public static Vehicle truck(String licensePlate) {
        return of(licensePlate, VehicleType.TRUCK);
    }

    // ── Getters ────────────────────────────────────────────────

    public String getLicensePlate() { return licensePlate; }
    public VehicleType getType()   { return type; }

    @Override
    public String toString() {
        return String.format("%s[%s]", type, licensePlate);
    }
}
