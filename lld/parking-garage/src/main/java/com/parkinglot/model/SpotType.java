package com.parkinglot.model;

import java.util.Set;

public enum SpotType {

    COMPACT(Set.of(VehicleType.MOTORCYCLE)),
    REGULAR(Set.of(VehicleType.MOTORCYCLE, VehicleType.CAR)),
    LARGE(Set.of(VehicleType.MOTORCYCLE, VehicleType.CAR, VehicleType.TRUCK));

    private final Set<VehicleType> allowedVehicles;

    SpotType(Set<VehicleType> allowedVehicles) {
        this.allowedVehicles = allowedVehicles;
    }

    public boolean canFit(VehicleType vehicleType) {
        return allowedVehicles.contains(vehicleType);
    }
}
