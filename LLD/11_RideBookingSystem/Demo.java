package uber;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FAANG System Design Demo: Ride Booking System ===");

        RideBookingService service = RideBookingService.getInstance();

        // 1. Register Riders
        Rider r1 = new Rider("r1", "Bob", new Location(0, 0));
        Rider r2 = new Rider("r2", "Charlie", new Location(10, 10));
        Rider r3 = new Rider("r3", "Grace", new Location(15, 15));

        service.registerRider(r1);
        service.registerRider(r2);
        service.registerRider(r3);

        // 2. Register Drivers
        Driver d1 = new Driver("d1", "David", new Location(2, 2)); // OFFLINE
        Driver d2 = new Driver("d2", "Evan", new Location(5, 5));
        Driver d3 = new Driver("d3", "Frank", new Location(20, 20));

        d2.setStatus(DriverStatus.AVAILABLE);
        d3.setStatus(DriverStatus.AVAILABLE);

        service.registerDriver(d1);
        service.registerDriver(d2);
        service.registerDriver(d3);

        System.out.println("Riders registered: Bob at (0,0), Charlie at (10,10).");
        System.out.println("Drivers registered: David at (2,2) [OFFLINE], Evan at (5,5) [AVAILABLE], Frank at (20,20) [AVAILABLE].");

        // 3. Scenario 1: Bob requests ride
        System.out.println("\n--- Bob requests ride to (5,0) ---");
        // Evan should match, since David is offline.
        Ride ride1 = service.requestRide("r1", new Location(5, 0));

        // 4. Scenario 2: David goes ONLINE, Charlie requests ride
        System.out.println("\n--- David goes ONLINE/AVAILABLE, Charlie requests ride to (12,12) ---");
        d1.setStatus(DriverStatus.AVAILABLE);
        // David is at (2,2), Frank is at (20,20). Charlie is at (10,10).
        // Distance to David is 11.3. Distance to Frank is 14.1. David matches.
        Ride ride2 = service.requestRide("r2", new Location(12, 12));

        // 5. Scenario 3: Trip Lifecycle
        System.out.println("\n--- Trip Lifecycle for Bob's Ride ---");
        if (ride1 != null) {
            service.startRide(ride1.getRideId());
            service.completeRide(ride1.getRideId());
        }

        // 6. Concurrency Match Check
        System.out.println("\n--- Concurrency check: Multiple riders requesting rides concurrently ---");
        System.out.println("Only David and Frank are AVAILABLE. Bob and Grace request rides concurrently.");

        d1.setStatus(DriverStatus.AVAILABLE);
        d2.setStatus(DriverStatus.BUSY); // Evan busy
        d3.setStatus(DriverStatus.AVAILABLE); // Frank available

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> service.requestRide("r1", new Location(3, 3)));
        executor.submit(() -> service.requestRide("r3", new Location(18, 18)));

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("\n=== Ride Booking Demo Finished successfully ===");
    }
}
