package com.parkinglot;

import com.parkinglot.exception.GarageFullException;
import com.parkinglot.garage.ParkingGarage;
import com.parkinglot.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Demo driver with narrated scenarios for interview walkthrough.
 */
public class ParkingGarageApp {

    private static ParkingGarage garage;

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════╗");
        System.out.println("║      Parking Garage — LLD Demo                ║");
        System.out.println("╚═══════════════════════════════════════════════╝\n");

        buildGarage();

        scenario1_parkVehicles();
        scenario2_unparkWithFee();
        scenario3_vehicleFitRules();
        scenario4_garageFull();
        scenario5_lookupByPlate();
        scenario6_finalStatus();
    }

    private static void buildGarage() {
        section("Setup: Build a 3-floor garage");
        System.out.println("  Floor 1: 5 compact, 10 regular, 2 large");
        System.out.println("  Floor 2: 5 compact, 10 regular, 2 large");
        System.out.println("  Floor 3: 0 compact,  5 regular, 5 large\n");

        garage = ParkingGarage.builder("Downtown Garage")
                .addFloor(1, 5, 10, 2)
                .addFloor(2, 5, 10, 2)
                .addFloor(3, 0, 5, 5)
                .build();

        System.out.printf("  Total capacity: %d spots%n%n", garage.totalCapacity());
    }

    // ── Scenario 1: Park various vehicles ───────────────────────

    private static void scenario1_parkVehicles() {
        section("Scenario 1: Park vehicles of different types");

        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);

        ParkingTicket t1 = parkVehicle(Vehicle.car("ABC-1234"), twoHoursAgo);
        ParkingTicket t2 = parkVehicle(Vehicle.car("DEF-5678"), twoHoursAgo);
        ParkingTicket t3 = parkVehicle(Vehicle.motorcycle("MOTO-001"), twoHoursAgo);
        ParkingTicket t4 = parkVehicle(Vehicle.truck("TRUCK-99"), twoHoursAgo);

        System.out.println();
        garage.printStatus();
    }

    // ── Scenario 2: Unpark and calculate fee ────────────────────

    private static void scenario2_unparkWithFee() {
        section("Scenario 2: Unpark vehicles and calculate fees");
        System.out.println("  Fees: Motorcycle $2/hr, Car $5/hr, Truck $10/hr");
        System.out.println("  Grace period: first 15 minutes free\n");

        // Car parked 2 hours ago → $10
        ParkingTicket carTicket = garage.findByLicensePlate("ABC-1234");
        ParkingTicket paid = garage.unparkVehicle(carTicket.getTicketId());
        System.out.printf("  Unparked %s → fee: $%s (duration: %s)%n",
                paid.getVehicle(), paid.getFee().toPlainString(), formatDuration(paid));

        // Motorcycle parked 2 hours ago → $4
        ParkingTicket motoTicket = garage.findByLicensePlate("MOTO-001");
        paid = garage.unparkVehicle(motoTicket.getTicketId());
        System.out.printf("  Unparked %s → fee: $%s (duration: %s)%n",
                paid.getVehicle(), paid.getFee().toPlainString(), formatDuration(paid));

        // Park a car for only 10 minutes (within grace period) → $0
        Instant tenMinutesAgo = Instant.now().minus(10, ChronoUnit.MINUTES);
        ParkingTicket shortStay = garage.parkVehicle(Vehicle.car("SHORT-01"), tenMinutesAgo);
        paid = garage.unparkVehicle(shortStay.getTicketId());
        System.out.printf("  Unparked %s → fee: $%s (duration: %s — grace period!)%n%n",
                paid.getVehicle(), paid.getFee().toPlainString(), formatDuration(paid));
    }

    // ── Scenario 3: Vehicle fit rules ───────────────────────────

    private static void scenario3_vehicleFitRules() {
        section("Scenario 3: Vehicle fit rules (smallest spot first)");
        System.out.println("  MOTORCYCLE → COMPACT > REGULAR > LARGE");
        System.out.println("  CAR        → REGULAR > LARGE");
        System.out.println("  TRUCK      → LARGE only\n");

        ParkingTicket motoTicket = parkVehicle(Vehicle.motorcycle("MOTO-002"), Instant.now());
        System.out.printf("    → assigned to %s spot (prefers smallest)%n",
                motoTicket.getSpot().getType());

        ParkingTicket carTicket = parkVehicle(Vehicle.car("CAR-NEW"), Instant.now());
        System.out.printf("    → assigned to %s spot%n",
                carTicket.getSpot().getType());

        ParkingTicket truckTicket = parkVehicle(Vehicle.truck("TRUCK-02"), Instant.now());
        System.out.printf("    → assigned to %s spot (only option)%n%n",
                truckTicket.getSpot().getType());
    }

    // ── Scenario 4: Garage full ─────────────────────────────────

    private static void scenario4_garageFull() {
        section("Scenario 4: Fill the garage and handle overflow");

        // Fill remaining large spots with trucks
        int trucksParked = 0;
        try {
            for (int i = 10; i < 30; i++) {
                garage.parkVehicle(Vehicle.truck("TRUCK-" + i), Instant.now());
                trucksParked++;
            }
        } catch (GarageFullException e) {
            System.out.printf("  Parked %d more trucks before full%n", trucksParked);
            System.out.printf("  GarageFullException: %s%n", e.getMessage());
        }

        System.out.printf("  Is full for TRUCK? %s%n", garage.isFull(VehicleType.TRUCK));
        System.out.printf("  Is full for CAR?   %s%n", garage.isFull(VehicleType.CAR));
        System.out.printf("  Is garage totally full? %s%n%n", garage.isFull());
    }

    // ── Scenario 5: Look up vehicle ─────────────────────────────

    private static void scenario5_lookupByPlate() {
        section("Scenario 5: Look up a parked vehicle by license plate");

        ParkingTicket ticket = garage.findByLicensePlate("DEF-5678");
        System.out.printf("  Found: %s%n", ticket);
        System.out.printf("  Vehicle: %s | Spot: %d (Floor %d, %s) | Status: %s%n%n",
                ticket.getVehicle(), ticket.getSpot().getSpotId(),
                ticket.getSpot().getFloor(), ticket.getSpot().getType(), ticket.getStatus());
    }

    // ── Scenario 6: Final status ────────────────────────────────

    private static void scenario6_finalStatus() {
        section("Scenario 6: Final garage status");
        garage.printStatus();
        System.out.printf("  Active tickets: %d%n", garage.activeTicketCount());
    }

    // ── Helpers ─────────────────────────────────────────────────

    private static ParkingTicket parkVehicle(Vehicle vehicle, Instant entryTime) {
        ParkingTicket ticket = garage.parkVehicle(vehicle, entryTime);
        System.out.printf("  [Ticket %d] Parked %s → Floor %d, Spot %d (%s)%n",
                ticket.getTicketId(), vehicle,
                ticket.getSpot().getFloor(), ticket.getSpot().getSpotId(),
                ticket.getSpot().getType());
        return ticket;
    }

    private static String formatDuration(ParkingTicket ticket) {
        long minutes = ticket.getDuration().toMinutes();
        if (minutes < 60) return minutes + " min";
        return String.format("%dh %dm", minutes / 60, minutes % 60);
    }

    private static void section(String title) {
        System.out.println("───────────────────────────────────────────────");
        System.out.println(title);
        System.out.println("───────────────────────────────────────────────");
    }
}
