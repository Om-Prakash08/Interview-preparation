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
| `ParkingSpotType` | Enum | `SMALL, MEDIUM, LARGE` |
| `Vehicle` | Abstract Class | Holds `licensePlate` + `VehicleType` |
| `Car / Motorcycle / Truck / Van` | Concrete | Extends `Vehicle` |
| `ParkingSpot` | Abstract Class | Tracks occupancy + defines `canFit()` contract |
| `SmallSpot / MediumSpot / LargeSpot` | Concrete | Extends `ParkingSpot`, implements `canFit()` |
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
    ├── CashPaymentStrategy
    ├── CreditCardPaymentStrategy
    └── MobileWalletPaymentStrategy
```
**Why?** Decouples pricing rules and payment methods from the core domain. Adding a new payment type requires zero changes to `ParkingLot`.

### 🔷 Singleton — ParkingLot
```java
public static synchronized ParkingLot getInstance();
```
**Why?** One central coordinator manages all levels and tickets.

### 🔷 Abstract Class Hierarchy — Vehicle & ParkingSpot
Both `Vehicle` and `ParkingSpot` follow the same pattern: abstract base class with concrete subclasses.
- **Vehicle** → `Car`, `Motorcycle`, `Truck`, `Van`
- **ParkingSpot** → `SmallSpot`, `MediumSpot`, `LargeSpot`

Spot size naming (`SMALL/MEDIUM/LARGE`) is intentionally decoupled from vehicle names — a spot's physical size is independent of what happens to park in it.

### 🔷 Class Skeleton
```java
public enum VehicleType     { MOTORCYCLE, CAR, TRUCK, VAN }
public enum ParkingSpotType { SMALL, MEDIUM, LARGE }

public abstract class Vehicle {
    private final String licensePlate;
    private final VehicleType type;
    public String getLicensePlate();
    public VehicleType getType();
}

public abstract class ParkingSpot {
    private final String spotId;
    private final ParkingSpotType type;
    private boolean isFree;
    private Vehicle vehicle;

    public abstract boolean canFit(VehicleType vehicleType); // each subclass decides

    public synchronized boolean park(Vehicle v);
    public synchronized void removeVehicle();
}

// Each subclass owns its own acceptance logic
class SmallSpot  extends ParkingSpot { canFit → MOTORCYCLE only       }
class MediumSpot extends ParkingSpot { canFit → MOTORCYCLE or CAR     }
class LargeSpot  extends ParkingSpot { canFit → all vehicle types     }

public class Level {
    private final String levelId;
    private final List<ParkingSpot> spots;

    public synchronized ParkingSpot parkVehicle(Vehicle v);
    public synchronized void freeSpot(ParkingSpot spot);
}

public class Ticket {
    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSpot parkingSpot;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private double amount;
    private TicketStatus status; // ACTIVE | PAID

    public synchronized void pay(double amount);
}

public class ParkingLot {           // Singleton
    private final List<Level> levels;
    private final FeeCalculator feeCalculator;

    public static synchronized ParkingLot getInstance();
    public synchronized Ticket issueTicket(Vehicle vehicle);
    public synchronized double releaseVehicle(Ticket ticket, PaymentStrategy payment);
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
// → Scans levels, calls spot.canFit(vehicle.getType()),
//   parks vehicle in first free compatible spot, returns Ticket
```

### Exit Flow
```java
lot.releaseVehicle(ticket, new MobileWalletPaymentStrategy("+91-9999"));
// → Calculates fee (HourlyFeeCalculator), processes payment, frees the spot
```

### Spot Matching Logic (inside `Level`)
```
for each spot in spots:
    if spot.isFree() && spot.canFit(vehicle.getType()):
        spot.park(vehicle)
        return spot
```
> **Key design decision:** `canFit()` lives in `ParkingSpot` subclasses, not in `Level`.
> `Level` only asks *"can you fit?"* — each spot answers for itself.

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| Add monthly subscription pricing | Implement `MonthlyFeeCalculator implements FeeCalculator` |
| Add crypto payment | Implement `CryptoPaymentStrategy implements PaymentStrategy` |
| Add EV charging spots | `class EVSpot extends LargeSpot` — override `canFit()`, add `startCharging()` |
| Add handicapped spots | `class HandicappedSpot extends ParkingSpot` — own `canFit()` logic |
| Add spot reservation | Add `reserve()` on `ParkingSpot`, pre-booking logic in `ParkingLot` |
| Multi-location parking chain | `ParkingLotNetwork` aggregates multiple `ParkingLot` Singletons |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `ParkingLot.issueTicket` | `synchronized` | Prevent two gates grabbing the same spot |
| `ParkingSpot.park` | `synchronized` | Atomic occupy check-and-set |
| `Level.parkVehicle` | `synchronized` | Prevent concurrent spot scan collision |
| `Ticket.pay` | `synchronized` | Prevent double payment race |
