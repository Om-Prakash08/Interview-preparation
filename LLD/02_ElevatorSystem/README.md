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
| `Request` | Class | Holds `floor` + `Direction` (hall call or cabin call) |
| `Elevator` | Class | Manages current state (`floor`, `direction`) and request queues (`upQueue`, `downQueue`) |
| `ElevatorController` | Class | Routes incoming requests to the best elevator by cost score |

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

public class Request {
    private final int floor;
    private final Direction direction;

    public Request(int floor, Direction direction) { ... }
    public int getFloor()           { return floor; }
    public Direction getDirection() { return direction; }
}

public class Elevator {
    private final int id;
    private int currentFloor;
    private Direction direction;
    private final PriorityQueue<Integer> upQueue;   // Min-Heap
    private final PriorityQueue<Integer> downQueue; // Max-Heap

    public void addRequest(int floor, Direction reqDir);
    public boolean hasRequests();
    public void processNextRequest();
}

public class ElevatorController {
    private final List<Elevator> elevators;

    public void dispatch(Request request); // Routes to best elevator by cost score
    private int calculateCost(Elevator elevator, Request request);
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
controller.dispatch(new Request(3, Direction.UP));
// → Scores each elevator by distance + direction compatibility
// → Assigns to best elevator via controller.dispatch()
```

### Handle Cabin Call (Internal)
```java
e1.addRequest(8, Direction.IDLE); // passenger inside e1 selects floor 8
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

### ElevatorController Cost Scoring
```
IDLE elevator                            → cost = distance
Moving TOWARDS request in same direction → cost = distance       (best match)
Moving TOWARDS request, opposite dir     → cost = distance + 5
Already passed the floor                 → cost = distance + 20   (worst match)
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
