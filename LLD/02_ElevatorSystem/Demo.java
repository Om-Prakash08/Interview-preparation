package elevator;

import java.util.ArrayList;
import java.util.List;

public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FAANG System Design Demo: Elevator Control System ===");

        // Setup: 2 Elevators, Max Floor = 10
        Elevator e1 = new Elevator(1, 10);
        Elevator e2 = new Elevator(2, 10);

        List<Elevator> elevators = new ArrayList<>();
        elevators.add(e1);
        elevators.add(e2);

        ElevatorController controller = new ElevatorController(elevators);

        // Simulate external hall calls
        System.out.println("\n--- Simulating External Hall Calls ---");
        // Passenger on Floor 3 wants to go UP
        controller.dispatch(new Request(3, Direction.UP));
        // Passenger on Floor 7 wants to go DOWN
        controller.dispatch(new Request(7, Direction.DOWN));

        // Simulate internal destination selections inside the elevator cabin
        System.out.println("\n--- Simulating Passengers Inside Cabins Selection ---");
        // Passenger in Elevator 1 requests floor 8
        System.out.println("[Cab 1] Passenger selects Floor 8");
        e1.addRequest(8, Direction.IDLE);

        // Passenger in Elevator 2 requests floor 2
        System.out.println("[Cab 2] Passenger selects Floor 2");
        e2.addRequest(2, Direction.IDLE);

        System.out.println("\n--- Starting Simulation Loop ---");
        boolean systemHasRequests = true;
        
        while (systemHasRequests) {
            systemHasRequests = false;
            for (Elevator elevator : elevators) {
                if (elevator.hasRequests()) {
                    systemHasRequests = true;
                    elevator.processNextRequest();
                }
            }
        }

        System.out.println("\n=== Demo Finished successfully ===");
    }
}
