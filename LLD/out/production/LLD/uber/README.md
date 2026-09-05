# 11. Ride Booking System (Mini Uber) — LLD Interview Guide

> **Framework:** Requirements → Entities → Class Design → Implementation → Extensibility

---

## ① Requirements (Clarify First!)

> *"Before I write any code, let me confirm the scope."*

**Functional Requirements:**
- Riders request rides by providing their **destination**
- System **matches the nearest available driver** to the rider
- Trip lifecycle: `REQUESTED → ACCEPTED → IN_PROGRESS → COMPLETED`
- Fare is calculated using a **pluggable pricing strategy**
- Driver status tracks: `AVAILABLE`, `BUSY`, `OFFLINE`
- Driver location updates dynamically (telemetry)

**Non-Functional Requirements:**
- **Thread-safe**: Multiple ride requests may arrive concurrently
- **Singleton** booking service — centralized coordination
- No two riders should be matched to the same driver simultaneously

**Out of Scope:**
- Payment processing
- Driver ratings / reviews
- Surge pricing triggers (can extend with Strategy Pattern)

---

## ② Entities (Nouns → Classes)

> *"I'll identify the key nouns to define my classes."*

| Entity | Type | Responsibility |
|---|---|---|
| `Location` | Class | `(x, y)` + `distanceTo(other)` — Euclidean distance |
| `DriverStatus` | Enum | `AVAILABLE, BUSY, OFFLINE` |
| `RideStatus` | Enum | `REQUESTED, ACCEPTED, IN_PROGRESS, COMPLETED, CANCELLED` |
| `Rider` | Class | id, name, current location |
| `Driver` | Class | id, name, location, status |
| `PricingStrategy` | Interface | `calculateFare(start, end)` |
| `DistancePricingStrategy` | Concrete | `baseFare + distance × perKmRate` |
| `Ride` | Class | rideId, rider, driver, pickup, destination, fare, status |
| `RideBookingService` | Singleton | Register riders/drivers, requestRide, startRide, completeRide |

---

## ③ Class Design (Design Patterns)

> *"I'll highlight the design patterns used and why."*

### 🔷 Strategy Pattern — Pricing
```
PricingStrategy (interface)
    └── DistancePricingStrategy(baseFare, perKmRate)
         → fare = baseFare + location.distanceTo(destination) × perKmRate
```
**Why?** Surge pricing, fixed rates, or subscription pricing can be plugged in with zero changes to `RideBookingService`.

### 🔷 Facade + Singleton — RideBookingService
```
RideBookingService (Singleton)
    ├── Map<String, Rider>  riders
    ├── Map<String, Driver> drivers
    ├── Map<String, Ride>   activeRides
    └── requestRide() → match → create Ride → transition statuses
```

### 🔷 Driver Matching Algorithm (Nearest Available)
```
requestRide(riderId, destination):
  1. Get rider's current location (rider.getLocation())
  2. Filter drivers: status == AVAILABLE
  3. Find driver with minimum distanceTo(rider.location)
  4. Mark driver BUSY
  5. Calculate fare via PricingStrategy
  6. Create Ride(REQUESTED), store in activeRides
```

### 🔷 Class Skeleton
```java
public class Location {
    private final double x, y;
    public double distanceTo(Location other); // √((x2-x1)² + (y2-y1)²)
}

public class Rider {
    @Synchronized public Location getLocation();
    @Synchronized public void setLocation(Location loc);
}

public class Driver {
    @Synchronized public Location getLocation();
    @Synchronized public void setLocation(Location loc);
    @Synchronized public DriverStatus getStatus();
    @Synchronized public void setStatus(DriverStatus status);
}

public interface PricingStrategy {
    double calculateFare(Location start, Location end);
}

public class Ride {
    private final String rideId;
    private final Rider rider;
    private final Driver driver;
    private final double fare;
    private RideStatus status;

    @Synchronized public void setStatus(RideStatus status);
}

public class RideBookingService {
    @Synchronized public static RideBookingService getInstance();
    public void registerRider(Rider rider);
    public void registerDriver(Driver driver);

    @Synchronized public Ride requestRide(String riderId, Location destination);
    @Synchronized public void startRide(String rideId);    // REQUESTED → IN_PROGRESS
    @Synchronized public void completeRide(String rideId); // IN_PROGRESS → COMPLETED; Driver → AVAILABLE
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Setup
```java
RideBookingService service = RideBookingService.getInstance();

Rider bob  = new Rider("r1", "Bob",  new Location(0, 0));
Driver eva = new Driver("d1", "Eva", new Location(5, 5));
eva.setStatus(DriverStatus.AVAILABLE);

service.registerRider(bob);
service.registerDriver(eva);
```

### Ride Lifecycle
```java
// 1. Request Ride (Bob wants to go to (5, 0))
Ride ride = service.requestRide("r1", new Location(5, 0));
// → Nearest available driver = Eva (distance ≈ 7.07 units)
// → ride.status = REQUESTED, eva.status = BUSY
// → fare = baseFare + 7.07 × perKmRate

// 2. Start Ride
service.startRide(ride.getRideId());
// → ride.status = IN_PROGRESS

// 3. Complete Ride
service.completeRide(ride.getRideId());
// → ride.status = COMPLETED
// → eva.status = AVAILABLE, eva.location = destination (5, 0)
```

### Ride Status Lifecycle
```
REQUESTED → IN_PROGRESS → COMPLETED
           ↘ CANCELLED (if driver/rider cancels before start)
```

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| Surge pricing | Add `SurgePricingStrategy implements PricingStrategy` |
| Ride cancellation | Add `cancelRide(rideId)` → set CANCELLED, free driver |
| Driver ratings | Add `double rating` to `Driver`, update on `completeRide` |
| Scheduled rides | Add `scheduledTime` to `Ride`, queue in `ScheduledRideService` |
| Pool rides (shared) | Add `List<Rider>` to `Ride`, route all to closest stop |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `requestRide()` | `@Synchronized` | Prevent two riders matching the same available driver |
| `Driver.setStatus` | `@Synchronized` | Safe status flip between AVAILABLE/BUSY |
| `Driver.setLocation` | `@Synchronized` | Safe telemetry updates from background location service |
| `Ride.setStatus` | `@Synchronized` | Prevent stale status reads during lifecycle transitions |
