# 11. Ride Booking System (Java LLD Solution)

This folder contains a complete, thread-safe Java implementation of a Ride Booking System (mini Uber).

Below is the **Complete Class Skeleton and API Design** so you can understand the entire system architecture, fields, and method signatures without looking at the source code.

---

## 1. Class Diagram / Architecture Skeleton

### Location & State Definitions
```java
@Getter
@AllArgsConstructor
public class Location {
    private final double x;
    private final double y;

    public double distanceTo(Location other); // Euclidean metric
}

public enum DriverStatus { AVAILABLE, BUSY, OFFLINE }
public enum RideStatus { REQUESTED, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED }
```

### Core User Entities
```java
public class Rider {
    @Getter private final String id;
    @Getter private final String name;
    private Location location;

    @Synchronized public Location getLocation();
    @Synchronized public void setLocation(Location loc);
}

public class Driver {
    @Getter private final String id;
    @Getter private final String name;
    private Location location;
    private DriverStatus status;

    @Synchronized public Location getLocation();
    @Synchronized public void setLocation(Location loc);
    @Synchronized public DriverStatus getStatus();
    @Synchronized public void setStatus(DriverStatus status);
}
```

### Strategy Pattern & Trip Records
```java
public interface PricingStrategy {
    double calculateFare(Location start, Location end);
}

class DistancePricingStrategy implements PricingStrategy {
    public DistancePricingStrategy(double baseFare, double perKmRate);
    @Override public double calculateFare(Location start, Location end);
}

@Getter
public class Ride {
    private final String rideId;
    private final Rider rider;
    private final Driver driver;
    private final Location pickup;
    private final Location destination;
    private final double fare;
    private RideStatus status;

    @Synchronized public RideStatus getStatus();
    @Synchronized public void setStatus(RideStatus status);
}
```

### Allocation Service (Facade / Dispatcher)
```java
public class RideBookingService {
    @Synchronized public static RideBookingService getInstance();
    public void registerRider(Rider rider);
    public void registerDriver(Driver driver);

    @Synchronized public Ride requestRide(String riderId, Location destination); // Matches closest available driver
    @Synchronized public void startRide(String rideId);
    @Synchronized public void completeRide(String rideId); // Frees driver & updates location
}
```

---

## 2. Core Workflow & Usage

Here is how passenger request flows and driver location allocations work:

```java
RideBookingService service = RideBookingService.getInstance();

// 1. Register users
Rider rider = new Rider("r1", "Bob", new Location(0, 0));
Driver driver = new Driver("d1", "Evan", new Location(5, 5));
driver.setStatus(DriverStatus.AVAILABLE);

service.registerRider(rider);
service.registerDriver(driver);

// 2. Request Ride (Euclidean proximity search, books closest driver)
Ride ride = service.requestRide("r1", new Location(5, 0)); // Matches Evan

// 3. Trip lifecycle progress
service.startRide(ride.getRideId());
service.completeRide(ride.getRideId()); // Evan is now AVAILABLE at destination (5,0)
```

---

## 3. Concurrency & Thread-Safety Details
- **Driver Double-Matching Prevention**: The `requestRide` dispatch algorithm is fully synchronized (`@Synchronized`) on the centralized booking coordinator. This ensures that only one passenger thread can scan and claim a specific available driver, preventing double-bookings.
- **Dynamic Thread Safety**: Member coordinates (`location`) and driver `status` are protected via synchronized monitor locks, allowing background location telemetry updates to execute safely.
