package parkinglot;

import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FAANG System Design Demo: Parking Lot ===");

        // 1. Initialize ParkingLot
        ParkingLot parkingLot = ParkingLot.getInstance("FAANG Silicon Valley Lot");
        // Add levels: Level 1 and Level 2, with 10 and 5 spots respectively.
        // Level spots distribution logic:
        // L1: 2 Motorcycle, 6 Compact, 2 Large
        // L2: 1 Motorcycle, 3 Compact, 1 Large
        parkingLot.addLevel(new Level("Level_1", 10));
        parkingLot.addLevel(new Level("Level_2", 5));

        System.out.println("Parking lot configured with 2 levels, total 15 spots.");

        // 2. Setup thread pool to simulate concurrent arrivals
        ExecutorService arrivalExecutor = Executors.newFixedThreadPool(5);
        List<Ticket> activeTickets = new Vector<>(); // Thread-safe collection for storing tickets

        System.out.println("\n--- Simulating Concurrent Arrivals ---");
        for (int i = 1; i <= 18; i++) {
            final int id = i;
            arrivalExecutor.submit(() -> {
                Vehicle vehicle;
                if (id % 4 == 0) {
                    vehicle = new Motorcycle("M-PLATE-" + id);
                } else if (id % 4 == 1) {
                    vehicle = new Car("C-PLATE-" + id);
                } else if (id % 4 == 2) {
                    vehicle = new Truck("T-PLATE-" + id);
                } else {
                    vehicle = new Van("V-PLATE-" + id);
                }

                Ticket ticket = parkingLot.issueTicket(vehicle);
                if (ticket != null) {
                    activeTickets.add(ticket);
                }
            });
        }

        // Wait for arrivals to finish
        arrivalExecutor.shutdown();
        try {
            arrivalExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 3. Simulating concurrent departures and exits
        System.out.println("\n--- Simulating Concurrent Departures ---");
        ExecutorService departureExecutor = Executors.newFixedThreadPool(4);
        
        for (Ticket ticket : activeTickets) {
            departureExecutor.submit(() -> {
                // Introduce slight random delay to simulate parking duration
                try {
                    Thread.sleep((long) (Math.random() * 200));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                PaymentStrategy paymentStrategy;
                if (Math.random() > 0.5) {
                    paymentStrategy = new CreditCardPaymentStrategy("4321-8765-9876-1234", "FAANG Candidate");
                } else {
                    paymentStrategy = new MobileWalletPaymentStrategy("+1-555-0199");
                }

                parkingLot.releaseVehicle(ticket, paymentStrategy);
            });
        }

        departureExecutor.shutdown();
        try {
            departureExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\n=== Demo Finished successfully ===");
    }
}
