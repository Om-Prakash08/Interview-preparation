package uber;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RideBookingService {
    private static RideBookingService instance;
    private final Map<String, Driver> drivers;
    private final Map<String, Rider> riders;
    private final Map<String, Ride> activeRides;
    private final PricingStrategy pricingStrategy;

    private RideBookingService() {
        this.drivers = new ConcurrentHashMap<>();
        this.riders = new ConcurrentHashMap<>();
        this.activeRides = new ConcurrentHashMap<>();
        this.pricingStrategy = new DistancePricingStrategy(5.0, 2.0);
    }

    public static synchronized RideBookingService getInstance() {
        if (instance == null) instance = new RideBookingService();
        return instance;
    }

    public void registerRider(Rider rider)   { riders.put(rider.getId(), rider); }
    public void registerDriver(Driver driver) { drivers.put(driver.getId(), driver); }

    public synchronized Ride requestRide(String riderId, Location destination) {
        Rider rider = riders.get(riderId);
        if (rider == null) return null;

        Location pickup = rider.getLocation();
        Driver closestDriver = null;
        double minDistance = Double.MAX_VALUE;

        for (Driver driver : drivers.values()) {
            if (driver.getStatus() == DriverStatus.AVAILABLE) {
                double dist = driver.getLocation().distanceTo(pickup);
                if (dist < minDistance) { minDistance = dist; closestDriver = driver; }
            }
        }
        if (closestDriver == null) {
            System.out.printf("[Ride Request Failed] No available drivers near %s!%n", rider.getName());
            return null;
        }

        closestDriver.setStatus(DriverStatus.BUSY);
        double fare = pricingStrategy.calculateFare(pickup, destination);
        String rideId = "RIDE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Ride ride = new Ride(rideId, rider, closestDriver, pickup, destination, fare);
        activeRides.put(rideId, ride);

        System.out.printf("[Ride Match] Ride %s matched. Driver %s picking up %s. Est. Fare: $%.2f%n",
                rideId, closestDriver.getName(), rider.getName(), fare);
        return ride;
    }

    public synchronized void startRide(String rideId) {
        Ride ride = activeRides.get(rideId);
        if (ride != null) { ride.setStatus(RideStatus.IN_PROGRESS); System.out.printf("[Ride Started] Trip %s is now IN PROGRESS.%n", rideId); }
    }

    public synchronized void completeRide(String rideId) {
        Ride ride = activeRides.get(rideId);
        if (ride != null) {
            ride.setStatus(RideStatus.COMPLETED);
            Driver driver = ride.getDriver();
            driver.setStatus(DriverStatus.AVAILABLE);
            driver.setLocation(ride.getDestination());
            activeRides.remove(rideId);
            System.out.printf("[Ride Completed] Trip %s completed. Driver %s is AVAILABLE at destination.%n", rideId, driver.getName());
        }
    }
}
