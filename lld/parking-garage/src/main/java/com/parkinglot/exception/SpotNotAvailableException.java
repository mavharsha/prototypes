package com.parkinglot.exception;

public class SpotNotAvailableException extends RuntimeException {

    public SpotNotAvailableException(long spotId) {
        super("Spot " + spotId + " is not available");
    }

    public SpotNotAvailableException(String message) {
        super(message);
    }
}
