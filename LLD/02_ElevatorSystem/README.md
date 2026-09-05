# 02. Elevator Control System — LLD Interview Guide

> **Framework:** Requirements → Entities → Class Design → Implementation → Extensibility

---

## ① Requirements (Clarify First!)

> *"Before I write any code, let me confirm the scope."*

**Functional Requirements:**
- Building has multiple **elevators** and multiple **floors**
- Two types of requests:
  - **External (Hall Call):** User presses UP/DOWN on a floor
  - **Internal (Cabin Call):** User presses a floor button inside the elevator
- A **Dispatcher** routes each request to the most suitable elevator
- Each elevator runs on its own **background thread**, continuously scanning for requests

**Non-Functional Requirements:**
- **Concurrent**: Each elevator is an independent thread; dispatcher is thread-safe
- Algorithm: **LOOK/SCAN** — elevator services requests in current direction before reversing
- Elevators should not busy-poll; they must **sleep** when idle and **wake up** on new requests

**Out of Scope:**
- Weight sensors / overload detection
- Priority passengers (VIP / emergency)

---

## ② Entities (Nouns → Classes)

> *"I'll identify the key nouns to define my classes."*

| Entity | Type | Responsibility |
|---|---|---|
| `Direction` | Enum | `UP, DOWN, IDLE` |
| `Request` | Class | Holds `floor` + `Direction` (hall call) |
| `Elevator` | Class (Runnable) | Owns floor request queues, runs LOOK algorithm |
| `Dispatcher` | Class | Routes incoming requests to the best elevator |

---

## ③ Class Design (Design Patterns)

> *"I'll highlight the design patterns used and why."*

### 🔷 Active Object Pattern — Elevator as Runnable
```
Elevator implements Runnable
    ├── boolean[] upRequests   (indexed by floor)
    ├── boolean[] downRequests (indexed by floor)
    └── run() → LOOK/SCAN loop (background thread)
```
**Why?** Each elevator independently processes its own queue without a shared central scheduler. This is more scalable than a master-controller approach.

### 🔷 Wait/Notify — Idle Elevator Sleep
```java
// Inside Elevator.run() — idles when no requests
synchronized(lockObj) { lockObj.wait(); }

// Dispatcher wakes it up on new request
synchronized(lockObj) { lockObj.notifyAll(); }
```
**Why?** Avoids busy-polling and wasted CPU cycles when the elevator is idle.

### 🔷 Class Skeleton
```java
public enum Direction { UP, DOWN, IDLE }

public class Request {
    private final int floor;
    private final Direction direction;
}

public class Elevator implements Runnable {
    private final int id;
    private int currentFloor;
    private Direction direction;
    private final boolean[] upRequests;
    private final boolean[] downRequests;
    private final Object lockObj = new Object();

    @Synchronized("lockObj") public void addRequest(int floor, Direction dir);
    @Synchronized("lockObj") public int getCurrentFloor();
    @Override public void run(); // LOOK algorithm loop
}

public class Dispatcher {
    private final List<Elevator> elevators;

    public void addElevator(Elevator elevator);
    public void handleRequest(Request request); // Routes to closest compatible elevator
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Setup & Start Threads
```java
Dispatcher dispatcher = new Dispatcher();
Elevator e1 = new Elevator(1, 10); // id=1, maxFloor=10
Elevator e2 = new Elevator(2, 10);

dispatcher.addElevator(e1);
dispatcher.addElevator(e2);

new Thread(e1).start(); // e1 starts LOOK loop, waits for requests
new Thread(e2).start();
```

### Handle Hall Call (External)
```java
dispatcher.handleRequest(new Request(3, Direction.UP));
// → Dispatcher scores each elevator by |currentFloor - 3| + direction compatibility
// → Assigns to closest available elevator, calls addRequest() + notifyAll()
```

### Handle Cabin Call (Internal)
```java
e1.addRequest(8, Direction.UP); // Passenger inside e1 wants floor 8
// → Sets upRequests[8] = true, notifies e1's run() loop
```

### LOOK Algorithm (inside `Elevator.run()`)
```
while running:
    if going UP:
        service all upRequests above currentFloor (in order)
        if none → switch to DOWN
    if going DOWN:
        service all downRequests below currentFloor (in order)
        if none → switch to IDLE → wait()
    openDoor() → sleep 800ms
```

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| Priority/VIP elevator | Add `priority` field to `Request`, update Dispatcher scoring |
| Emergency mode (fire) | Add `EmergencyState`, override `run()` to home to ground floor |
| Dispatcher algorithm swap | Extract `DispatchStrategy` interface (Strategy Pattern) |
| Capacity limits / overload | Add `currentLoad` field to `Elevator`, check before `addRequest` |
| Multiple buildings | Wrap in `Building` class containing its own `Dispatcher` |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `Elevator.addRequest` | `@Synchronized("lockObj")` | Protect request arrays from concurrent writes |
| `Elevator.run()` (idle) | `lockObj.wait()` | Sleep without busy-polling |
| `Dispatcher.handleRequest` | `lockObj.notifyAll()` | Wake correct elevator thread on new request |
| `getCurrentFloor / getDirection` | `@Synchronized("lockObj")` | Consistent reads for dispatcher scoring |
