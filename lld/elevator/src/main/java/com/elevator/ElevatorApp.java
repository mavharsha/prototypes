package com.elevator;

import com.elevator.model.Direction;
import com.elevator.system.ElevatorSystem;

import java.util.List;

/**
 * Demo driver with narrated scenarios for interview walkthrough.
 */
public class ElevatorApp {

    private static ElevatorSystem system;

    public static void main(String[] args) {
        System.out.println("╔═══════════════════════════════════════════════╗");
        System.out.println("║       Elevator System — LLD Demo              ║");
        System.out.println("╚═══════════════════════════════════════════════╝\n");

        system = new ElevatorSystem(3, 1, 10);
        System.out.println("  Created system: 3 elevators, floors 1–10\n");

        scenario1_singleRequest();
        scenario2_multipleRequests();
        scenario3_cabinCalls();
        scenario4_dispatchOptimization();
        scenario5_maintenance();
        scenario6_fullSimulation();
    }

    // ── Scenario 1: Single hall call ────────────────────────────

    private static void scenario1_singleRequest() {
        section("Scenario 1: Single hall call — person at floor 5 going UP");

        int elevatorId = system.requestElevator(5, Direction.UP);
        System.out.printf("  Dispatched: Elevator %d assigned%n", elevatorId);
        system.printStatus();

        // Run until elevator reaches floor 5
        List<String> events = system.runToCompletion();
        events.forEach(e -> System.out.println("  " + e));

        System.out.println();
        system.printStatus();
    }

    // ── Scenario 2: Multiple hall calls — dispatcher picks best ─

    private static void scenario2_multipleRequests() {
        section("Scenario 2: Multiple hall calls — dispatcher picks nearest elevator");

        // Reset: all elevators are now at various positions from scenario 1
        System.out.println("  Current positions:");
        system.getElevators().forEach(e ->
                System.out.printf("    Elevator %d at floor %d (%s)%n",
                        e.getId(), e.getCurrentFloor(), e.getState()));
        System.out.println();

        // Request from floor 8 going DOWN
        int id1 = system.requestElevator(8, Direction.DOWN);
        System.out.printf("  Floor 8 DOWN → Elevator %d%n", id1);

        // Request from floor 2 going UP
        int id2 = system.requestElevator(2, Direction.UP);
        System.out.printf("  Floor 2 UP   → Elevator %d%n", id2);

        // Request from floor 3 going UP
        int id3 = system.requestElevator(3, Direction.UP);
        System.out.printf("  Floor 3 UP   → Elevator %d%n", id3);

        system.printStatus();

        List<String> events = system.runToCompletion();
        events.forEach(e -> System.out.println("  " + e));
        System.out.println();
    }

    // ── Scenario 3: Cabin calls (pressing buttons inside) ───────

    private static void scenario3_cabinCalls() {
        section("Scenario 3: Cabin calls — passengers press floor buttons inside");

        // First get an elevator to floor 1
        int id = system.requestElevator(1, Direction.UP);
        system.runToCompletion();

        // Now simulate: passenger gets in at floor 1, presses 7
        System.out.printf("  Passenger enters Elevator %d at floor %d%n",
                id, system.getElevator(id).getCurrentFloor());
        system.pressFloor(id, 7);
        System.out.printf("  Presses floor 7%n");

        // Another passenger at floor 3 calls the elevator going UP
        system.requestElevator(3, Direction.UP);
        System.out.println("  Meanwhile: hall call from floor 3 going UP");

        system.printStatus();

        // Step through and watch the elevator serve both
        List<String> events = system.runToCompletion();
        events.forEach(e -> System.out.println("  " + e));

        System.out.println();
        system.printStatus();
    }

    // ── Scenario 4: Dispatch optimization ───────────────────────

    private static void scenario4_dispatchOptimization() {
        section("Scenario 4: Dispatch optimization — elevator already heading that way");

        // Move elevator 1 to floor 1 and start it going UP to floor 10
        system.requestElevator(1, Direction.UP);
        system.runToCompletion();
        system.pressFloor(1, 10);
        System.out.println("  Elevator 1 heading UP to floor 10");

        // Now request from floor 6 going UP — should pick elevator 1 (on the way)
        int assigned = system.requestElevator(6, Direction.UP);
        System.out.printf("  Floor 6 UP → Elevator %d (should prefer the one already heading up)%n", assigned);

        system.printStatus();

        List<String> events = system.runToCompletion();
        events.forEach(e -> System.out.println("  " + e));
        System.out.println();
    }

    // ── Scenario 5: Elevator maintenance ────────────────────────

    private static void scenario5_maintenance() {
        section("Scenario 5: Take elevator offline for maintenance");

        System.out.printf("  Elevator 2 at floor %d — setting to MAINTENANCE%n",
                system.getElevator(2).getCurrentFloor());
        system.setMaintenance(2, true);

        system.printStatus();

        // Request should skip elevator 2
        int id = system.requestElevator(5, Direction.UP);
        System.out.printf("  Floor 5 UP → Elevator %d (skipped maintenance elevator)%n", id);

        // Bring elevator 2 back online
        system.setMaintenance(2, false);
        System.out.println("  Elevator 2 back online\n");
    }

    // ── Scenario 6: Full multi-request simulation ───────────────

    private static void scenario6_fullSimulation() {
        section("Scenario 6: Full simulation — multiple concurrent requests");

        system.runToCompletion(); // clear any pending

        // Burst of requests
        System.out.println("  Burst of hall calls:");
        int a = system.requestElevator(2, Direction.UP);
        System.out.printf("    Floor 2 UP   → Elevator %d%n", a);
        int b = system.requestElevator(9, Direction.DOWN);
        System.out.printf("    Floor 9 DOWN → Elevator %d%n", b);
        int c = system.requestElevator(5, Direction.UP);
        System.out.printf("    Floor 5 UP   → Elevator %d%n", c);

        System.out.println("\n  Running simulation:");
        system.printStatus();

        List<String> events = system.runToCompletion();
        events.forEach(e -> System.out.println("  " + e));

        System.out.println("\n  Final state:");
        system.printStatus();
    }

    // ── Helpers ─────────────────────────────────────────────────

    private static void section(String title) {
        System.out.println("───────────────────────────────────────────────");
        System.out.println(title);
        System.out.println("───────────────────────────────────────────────");
    }
}
