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

        // Start elevator threads
        Thread t1 = new Thread(e1);
        Thread t2 = new Thread(e2);
        t1.start();
        t2.start();

        Dispatcher dispatcher = new Dispatcher(elevators);

        try {
            // Simulate external hall calls
            System.out.println("\n--- Simulating External Hall Calls ---");
            // Passenger on Floor 3 wants to go UP
            dispatcher.dispatch(new Request(3, Direction.UP));
            // Passenger on Floor 7 wants to go DOWN
            dispatcher.dispatch(new Request(7, Direction.DOWN));

            Thread.sleep(2500);

            // Simulate internal destination selections inside the elevator cabin
            System.out.println("\n--- Simulating Passengers Inside Cabins Selection ---");
            // Passenger in Elevator 1 requests floor 8
            System.out.println("[Cab 1] Passenger selects Floor 8");
            e1.addRequest(8, Direction.IDLE);

            // Passenger in Elevator 2 requests floor 2
            System.out.println("[Cab 2] Passenger selects Floor 2");
            e2.addRequest(2, Direction.IDLE);

            // Wait for elevators to finish servicing requests
            Thread.sleep(8000);

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } finally {
            // Clean shutdown of threads
            t1.interrupt();
            t2.interrupt();
        }

        System.out.println("\n=== Demo Finished successfully ===");
    }
}
