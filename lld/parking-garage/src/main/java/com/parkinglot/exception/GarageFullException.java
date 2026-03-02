package com.parkinglot.exception;

import com.parkinglot.model.VehicleType;

public class GarageFullException extends RuntimeException {

    public GarageFullException(VehicleType vehicleType) {
        super("No available spot for " + vehicleType);
    }
}
