# 01. Parking Lot System (Java LLD Solution)

This folder contains a complete, thread-safe Java implementation of a Parking Lot System. 

Below is the **Complete Class Skeleton and API Design** so you can understand the entire system architecture, fields, and method signatures without looking at the source code.

---

## 1. Class Diagram / Architecture Skeleton

### Enums
```java
public enum VehicleType { MOTORCYCLE, CAR, TRUCK, VAN }
public enum ParkingSpotType { MOTORCYCLE, COMPACT, LARGE }
```

### Strategy Pattern (Pricing & Payments)
```java
public interface FeeCalculator {
    double calculateFee(LocalDateTime start, LocalDateTime end, VehicleType type);
}

public class HourlyFeeCalculator implements FeeCalculator {
    @Override
    public double calculateFee(LocalDateTime start, LocalDateTime end, VehicleType type) { ... }
}

public interface PaymentStrategy {
    void processPayment(double amount);
}

public class CreditCardPayment implements PaymentStrategy { ... }
public class MobileWalletPayment implements PaymentStrategy { ... }
```

### Core Entities
```java
// Lombok getter handles field reads, constructors are mapped
@Getter
public abstract class Vehicle {
    private final String licensePlate;
    private final VehicleType type;
}

class Car extends Vehicle { public Car(String lp) { super(lp, VehicleType.CAR); } }
class Motorcycle extends Vehicle { public Motorcycle(String lp) { super(lp, VehicleType.MOTORCYCLE); } }
class Truck extends Vehicle { public Truck(String lp) { super(lp, VehicleType.TRUCK); } }
class Van extends Vehicle { public Van(String lp) { super(lp, VehicleType.VAN); } }

public class ParkingSpot {
    @Getter private final String spotId;
    @Getter private final ParkingSpotType type;
    private boolean isFree;
    private Vehicle vehicle;

    @Synchronized public boolean isFree();
    @Synchronized public Vehicle getVehicle();
    @Synchronized public boolean park(Vehicle v);
    @Synchronized public void removeVehicle();
}

public class Level {
    @Getter private final String levelId;
    private final List<ParkingSpot> spots;

    public List<ParkingSpot> getSpots(); // returns unmodifiable list
    @Synchronized public ParkingSpot parkVehicle(Vehicle v);
    @Synchronized public void freeSpot(ParkingSpot spot);
}

@Getter
public class Ticket {
    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSpot parkingSpot;
    private final String levelId;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private double amount;
    private TicketStatus status; // ACTIVE, PAID

    @Synchronized public void pay(double amount);
}

public class ParkingLot {
    @Getter private final String name;
    private final List<Level> levels;
    private final FeeCalculator feeCalculator;

    @Synchronized public static ParkingLot getInstance();
    @Synchronized public void addLevel(Level level);
    @Synchronized public List<Level> getLevels();
    @Synchronized public Ticket issueTicket(Vehicle vehicle); // Match nearest spot & issue ticket
    @Synchronized public double releaseVehicle(Ticket ticket, PaymentStrategy payment); // Free spot & process payment
}
```

---

## 2. Core Workflow & Usage

Here is how the API is initialized and coordinated:

```java
// 1. Initialize Singleton Parking Lot
ParkingLot lot = ParkingLot.getInstance("FAANG Lot");
Level level1 = new Level("Level_1", 10);
lot.addLevel(level1);

// 2. Issue Ticket at Entrance Gate (Thread-Safe Proximity Allocation)
Vehicle car = new Car("CAR-1234");
Ticket ticket = lot.issueTicket(car); // Auto-allocates Level_1 COMPACT/LARGE spot

// 3. Process Exit Gate Payment & Release Spot
lot.releaseVehicle(ticket, new MobileWalletPayment("+1-555-0199"));
```

---

## 3. Concurrency & Thread-Safety Details
- **Gates Locking**: The entrance and exit gates in `ParkingLot` are fully synchronized (`@Synchronized`), guaranteeing that concurrent vehicles entering the lot do not read/claim the same empty spot.
- **Spot Locking**: Individual `ParkingSpot` actions (`park`, `removeVehicle`) are synchronized to secure the spot state variables.
- **Level Proximity Loop**: `Level` spot scanning is thread-safe (`@Synchronized`), preventing thread collision during proximity matching.
