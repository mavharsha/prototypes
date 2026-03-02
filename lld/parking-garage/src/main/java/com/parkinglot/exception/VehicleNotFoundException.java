package com.parkinglot.exception;

public class VehicleNotFoundException extends RuntimeException {

    public VehicleNotFoundException(String licensePlate) {
        super("Vehicle not found: " + licensePlate);
    }
}
