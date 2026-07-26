# 02. Elevator Control System (Java LLD Solution)

This folder contains a complete, concurrent Java implementation of an Elevator Control System.

Below is the **Complete Class Skeleton and API Design** so you can understand the entire system architecture, fields, and method signatures without looking at the source code.

---

## 1. Class Diagram / Architecture Skeleton

### Enums & Requests
```java
public enum Direction { UP, DOWN, IDLE }

@Getter
@RequiredArgsConstructor
public class Request {
    private final int floor;
    private final Direction direction; // UP or DOWN from the floor
}
```

### Core Entities
```java
public class Elevator implements Runnable {
    @Getter private final int id;
    private int currentFloor;
    private Direction direction;
    private final boolean[] upRequests;   // Size: maxFloor + 1
    private final boolean[] downRequests; // Size: maxFloor + 1
    private final int maxFloor;
    private final Object lockObj = new Object(); // Custom monitor lock

    @Synchronized("lockObj") public int getCurrentFloor();
    @Synchronized("lockObj") public Direction getDirection();
    @Synchronized("lockObj") public void addRequest(int floor, Direction reqDir);
    
    private void openDoor(); // door opens for 800ms
    
    @Override public void run(); // Background thread scan execution (LOOK / SCAN algorithm)
}

public class Dispatcher {
    private final List<Elevator> elevators;

    public void addElevator(Elevator elevator);
    public void handleRequest(Request request); // Routes external calls to closest compatible elevator
}
```

---

## 2. Core Workflow & Usage

Here is how the system is initialized and run concurrently:

```java
// 1. Create Dispatcher and start Elevator threads
Dispatcher dispatcher = new Dispatcher();
Elevator e1 = new Elevator(1, 10); // Elevator 1 up to floor 10
Elevator e2 = new Elevator(2, 10);

dispatcher.addElevator(e1);
dispatcher.addElevator(e2);

new Thread(e1).start();
new Thread(e2).start();

// 2. Dispatcher receives external hall calls
dispatcher.handleRequest(new Request(3, Direction.UP)); // routes to closest elevator

// 3. Cabin panel selection inside Elevator 1
e1.addRequest(8, Direction.UP); // Passenger wants to go to Floor 8
```

---

## 3. Concurrency & Thread-Safety Details
- **Background Execution**: Each elevator implements `Runnable` and loops on its own thread, simulating motion with sleep states.
- **Wait/Notify Model**: When an elevator has no requests, its background thread is put into a `wait()` state on `lockObj` (monitor lock). The dispatcher wakes it up by calling `lockObj.notifyAll()` when a new request is queued.
- **Custom Lock Monitor**: Lombok methods use `@Synchronized("lockObj")` to coordinate with manual `synchronized(lockObj)` blocks inside the running execution loop, avoiding deadlocks.
