# 02. Elevator Control System — LLD Interview Guide

> **Framework:** Requirements → Entities → Class Design → Implementation → Extensibility

---

## ① Requirements (Clarify First!)

> *"Before I write any code, let me confirm the scope."*

**Functional Requirements:**
- Building has multiple **elevators** and multiple **floors**
- Two types of requests:
  - **External (Hall Call):** User presses UP/DOWN button on a floor
  - **Internal (Cabin Call):** User presses a floor button inside the elevator
- An **ElevatorController** routes each request to the most suitable elevator based on distance and direction compatibility
- Elevators service requests using the **SCAN / LOOK algorithm** (handling all requests in current direction before reversing)

**Non-Functional Requirements:**
- **Deterministic & Synchronous**: Avoid over-complicating interviews with low-level multithreading primitives (`wait`, `notifyAll`, `synchronized`) unless explicitly requested by the interviewer. Focus on OOP design, algorithm complexity, and object boundaries.
- **Algorithm Optimization**: Use **PriorityQueue** data structures (Min-Heap for UP, Max-Heap for DOWN) to maintain optimal request processing order efficiently.

**Out of Scope:**
- Weight sensors / overload detection
- VIP / Emergency priority overrides

---

## ② Entities (Nouns → Classes)

> *"I'll identify the key nouns to define my classes."*

| Entity | Type | Responsibility |
|---|---|---|
| `Direction` | Enum | `UP, DOWN, IDLE` |
| `HallRequest` | Class | Holds pickup floor + required travel direction |
| `CabinRequest` | Class | Holds a destination selected inside one elevator |
| `Elevator` | Class | Manages current state (`floor`, `direction`) and request queues (`upQueue`, `downQueue`) |
| `ElevatorController` | Class | Routes hall calls using direction, idle-state, and distance priorities |

---

## ③ Class Design & Algorithmic Foundation

> *"I'll highlight the design patterns and algorithmic strategy used."*

### 🔷 Priority Queue-based SCAN Algorithm
```
Elevator
  ├── PriorityQueue<Integer> upQueue   (Min-Heap: processes ascending floors in order)
  ├── PriorityQueue<Integer> downQueue (Max-Heap: processes descending floors in order)
  └── processNextRequest()             (Services current direction first, then reverses)
```
**Why?** Dual Heaps naturally maintain sorted order for target floors. Inserting a new request is \(O(\log N)\) and retrieving the next target floor is \(O(1)\).

### 🔷 Class Skeleton

```java
public enum Direction { UP, DOWN, IDLE }

public class HallRequest {
    private final int pickupFloor;
    private final Direction direction;
}

public class CabinRequest {
    private final int destinationFloor;
}

public class Elevator {
    private final int id;
    private int currentFloor;
    private Direction direction;
    private final PriorityQueue<Integer> upQueue;   // Min-Heap
    private final PriorityQueue<Integer> downQueue; // Max-Heap

    public void addHallRequest(HallRequest request);
    public void addCabinRequest(CabinRequest request);
    public boolean hasRequests();
    public void processNextRequest();
}

public class ElevatorController {
    private final List<Elevator> elevators;

    public void requestElevator(HallRequest request);
    private Elevator selectBestElevator(HallRequest request);
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Setup
```java
Elevator e1 = new Elevator(1, 10); // id=1, maxFloor=10
Elevator e2 = new Elevator(2, 10);

ElevatorController controller = new ElevatorController(List.of(e1, e2));
```

### Handle Hall Call (External)
```java
controller.requestElevator(new HallRequest(3, Direction.UP));
// → Prefers a matching elevator already moving toward floor 3
// → Otherwise selects the nearest idle elevator, then the nearest fallback
```

### Handle Cabin Call (Internal)
```java
e1.addCabinRequest(new CabinRequest(8)); // passenger inside e1 selects floor 8
// → Automatically added to upQueue (floor > currentFloor)
```

### Simulation Loop
```java
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
```

### ElevatorController Selection Priorities
```
1. Nearest elevator moving toward the pickup floor in the requested direction
2. Nearest idle elevator
3. Nearest elevator overall (fallback)
```

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| Priority/VIP requests | Use a custom comparator in `PriorityQueue` with a `Priority` enum |
| Strategy Pattern | Extract `DispatchStrategy` interface from `ElevatorController` |
| Multi-Building | Introduce `Building` class containing its own `ElevatorController` |
| Overload Control | Add `currentCapacity` attribute to `Elevator` and check before dispatching |
