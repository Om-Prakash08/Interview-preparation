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
- A **Dispatcher** routes each request to the most suitable elevator
- Each elevator runs on its own **background thread**, continuously scanning for requests

**Non-Functional Requirements:**
- **Concurrent**: Each elevator is an independent thread; requests are thread-safe
- Algorithm: **LOOK/SCAN** — elevator services requests in current direction before reversing
- Elevators must **sleep** when idle and **wake up** on new requests (no busy-polling)

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
| `Elevator` | Class (Runnable) | Owns floor request arrays, runs LOOK algorithm |
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
**Why?** Each elevator independently processes its own queue without a shared central scheduler. More scalable than a master-controller approach.

### 🔷 Wait/Notify — Idle Elevator Sleep
```java
// Inside Elevator.run() — waits when no requests
synchronized (this) { wait(); }

// addRequest() wakes it up on new request
notifyAll();
```
**Why?** Avoids busy-polling and wasted CPU cycles when the elevator is idle.

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

public class Elevator implements Runnable {
    private final int id;
    private int currentFloor;
    private Direction direction;
    private final boolean[] upRequests;
    private final boolean[] downRequests;

    public int getId()                          { return id; }
    public synchronized int getCurrentFloor()   { return currentFloor; }
    public synchronized Direction getDirection() { return direction; }
    public synchronized void addRequest(int floor, Direction dir);

    @Override public void run(); // LOOK algorithm loop
}

public class Dispatcher {
    private final List<Elevator> elevators;

    public void dispatch(Request request); // routes to best elevator by cost score
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Setup & Start Threads
```java
Elevator e1 = new Elevator(1, 10); // id=1, maxFloor=10
Elevator e2 = new Elevator(2, 10);

new Thread(e1).start(); // starts LOOK loop, waits for requests
new Thread(e2).start();

Dispatcher dispatcher = new Dispatcher(List.of(e1, e2));
```

### Handle Hall Call (External)
```java
dispatcher.dispatch(new Request(3, Direction.UP));
// → Scores each elevator by distance + direction compatibility
// → Assigns to best elevator, calls addRequest() → notifyAll()
```

### Handle Cabin Call (Internal)
```java
e1.addRequest(8, Direction.IDLE); // passenger inside e1 selects floor 8
// → Sets upRequests[8] = true, notifies run() loop
```

### LOOK Algorithm (inside `Elevator.run()`)
```
while running:
    if IDLE → wait() until notifyAll()

    if going UP:
        currentFloor++
        if upRequests[currentFloor] → openDoor(), clear request
        if no more requests above  → switch to DOWN
        if no more requests below  → IDLE

    if going DOWN:
        currentFloor--
        if downRequests[currentFloor] → openDoor(), clear request
        if no more requests below  → switch to UP
        if no more requests above  → IDLE
```

### Dispatcher Cost Scoring
```
IDLE elevator        → cost = distance
Moving TOWARDS request in same direction → cost = distance       (best)
Moving TOWARDS request, opposite dir    → cost = distance + 5
Already passed the floor               → cost = distance + 20   (worst)
```

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| Priority/VIP requests | Add `priority` field to `Request`, update Dispatcher scoring |
| Emergency mode (fire) | Add `EmergencyMode` state, override `run()` to home to ground floor |
| Swap dispatch algorithm | Extract `DispatchStrategy` interface (Strategy Pattern) |
| Capacity limits / overload | Add `currentLoad` to `Elevator`, check before `addRequest()` |
| Multiple buildings | Wrap in `Building` class, each with its own `Dispatcher` |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `Elevator.addRequest()` | `synchronized` method | Protect request arrays from concurrent writes |
| `Elevator.run()` idle wait | `synchronized(this) { wait(); }` | Sleep without busy-polling |
| Wake up on new request | `notifyAll()` in `addRequest()` | Wake elevator thread when request arrives |
| `getCurrentFloor()` / `getDirection()` | `synchronized` method | Consistent reads for dispatcher scoring |
