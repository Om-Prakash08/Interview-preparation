# 01. Parking Lot System — LLD Interview Guide

> **Framework:** Requirements → Entities → Class Design → Implementation → Extensibility

---

## ① Requirements (Clarify First!)

> *"Before I write any code, let me confirm the scope."*

**Functional Requirements:**
- Support multiple vehicle types: `Motorcycle`, `Car`, `Truck`, `Van`
- Parking lot has multiple **Levels**, each with multiple **Spots** of different sizes
- Entrance gate: issue a **Ticket** when a vehicle parks
- Exit gate: calculate **fee** and process **payment**, then free the spot
- Match vehicle to the **nearest compatible spot** on entry

**Non-Functional Requirements:**
- **Thread-safe**: multiple entrance/exit gates operate concurrently
- **Singleton** parking lot — single point of coordination
- Fee strategy and payment method must be **swappable** (Strategy Pattern)

**Out of Scope (for this interview):**
- Online reservation / pre-booking
- Real-time GUI or sensor integration

---

## ② Entities (Nouns → Classes)

> *"I'll identify the key nouns in the problem to define my classes."*

| Entity | Type | Responsibility |
|---|---|---|
| `VehicleType` | Enum | `MOTORCYCLE, CAR, TRUCK, VAN` |
| `ParkingSpotType` | Enum | `MOTORCYCLE, COMPACT, LARGE` |
| `Vehicle` | Abstract Class | Holds `licensePlate` + `VehicleType` |
| `Car / Motorcycle / Truck / Van` | Concrete | Extends `Vehicle` |
| `ParkingSpot` | Class | Tracks occupancy + vehicle reference |
| `Level` | Class | Contains list of `ParkingSpot`s |
| `Ticket` | Class | Entry record: vehicle, spot, entryTime, amount |
| `ParkingLot` | Singleton | Orchestrates levels, issues/releases tickets |

---

## ③ Class Design (Design Patterns)

> *"I'll highlight the design patterns used and why."*

### 🔷 Strategy Pattern — Fee & Payment
```
FeeCalculator (interface)
    └── HourlyFeeCalculator

PaymentStrategy (interface)
    ├── CreditCardPayment
    └── MobileWalletPayment
```
**Why?** Decouples pricing rules and payment methods from the core domain. Adding a new payment type requires zero changes to `ParkingLot`.

### 🔷 Singleton — ParkingLot
```java
@Synchronized public static ParkingLot getInstance();
```
**Why?** One central coordinator manages all levels and tickets.

### 🔷 Class Skeleton
```java
public enum VehicleType    { MOTORCYCLE, CAR, TRUCK, VAN }
public enum ParkingSpotType{ MOTORCYCLE, COMPACT, LARGE  }

public abstract class Vehicle {
    private final String licensePlate;
    private final VehicleType type;
}

public class ParkingSpot {
    private final String spotId;
    private final ParkingSpotType type;
    private boolean isFree;
    private Vehicle vehicle;

    @Synchronized public boolean park(Vehicle v);
    @Synchronized public void removeVehicle();
}

public class Level {
    private final String levelId;
    private final List<ParkingSpot> spots;

    @Synchronized public ParkingSpot parkVehicle(Vehicle v);
    @Synchronized public void freeSpot(ParkingSpot spot);
}

public class Ticket {
    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSpot parkingSpot;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private double amount;

    @Synchronized public void pay(double amount);
}

public class ParkingLot {
    private final List<Level> levels;
    private final FeeCalculator feeCalculator;

    @Synchronized public static ParkingLot getInstance();
    @Synchronized public Ticket issueTicket(Vehicle vehicle);
    @Synchronized public double releaseVehicle(Ticket ticket, PaymentStrategy payment);
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Entry Flow
```java
ParkingLot lot = ParkingLot.getInstance("FAANG Lot");
lot.addLevel(new Level("L1", 10));

Vehicle car = new Car("CAR-1234");
Ticket ticket = lot.issueTicket(car);
// → Scans levels, finds nearest COMPACT/LARGE spot, parks vehicle, returns Ticket
```

### Exit Flow
```java
lot.releaseVehicle(ticket, new MobileWalletPayment("+91-9999"));
// → Calculates fee (HourlyFeeCalculator), processes payment, frees the spot
```

### Spot Matching Logic (inside `Level`)
```
for each spot in spots:
    if spot.isFree() && spot.type compatible with vehicle.type:
        spot.park(vehicle)
        return spot
```

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| Add monthly subscription pricing | Implement new `MonthlyFeeCalculator implements FeeCalculator` |
| Add crypto payment | Implement `CryptoPayment implements PaymentStrategy` |
| Add EV charging spots | Add `EV_CHARGING` to `ParkingSpotType` enum |
| Add spot reservation | Add `reserve()` on `ParkingSpot`, pre-booking logic in `ParkingLot` |
| Multi-location parking chain | `ParkingLotNetwork` aggregates multiple `ParkingLot` Singletons |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `ParkingLot.issueTicket` | `@Synchronized` | Prevent two gates grabbing same spot |
| `ParkingSpot.park` | `@Synchronized` | Atomic occupy check-and-set |
| `Level.parkVehicle` | `@Synchronized` | Prevent concurrent spot scan collision |
| `Ticket.pay` | `@Synchronized` | Prevent double payment race |
