package com.elevator.exception;

public class ElevatorNotFoundException extends RuntimeException {

    public ElevatorNotFoundException(int elevatorId) {
        super("Elevator " + elevatorId + " not found");
    }
}
