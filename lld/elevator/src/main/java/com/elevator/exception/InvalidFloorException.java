package com.elevator.exception;

public class InvalidFloorException extends RuntimeException {

    public InvalidFloorException(String message) {
        super(message);
    }

    public InvalidFloorException(int floor, int minFloor, int maxFloor) {
        super(String.format("Floor %d is out of range [%d, %d]", floor, minFloor, maxFloor));
    }
}
