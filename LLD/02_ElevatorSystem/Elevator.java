package elevator;

import java.util.Collections;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;

public class Elevator {
    private final int id;
    private int currentFloor;
    private Direction direction;
    private final PriorityQueue<Integer> upQueue;
    private final PriorityQueue<Integer> downQueue;
    private final Set<Integer> upStops;
    private final Set<Integer> downStops;
    private final int maxFloor;

    public Elevator(int id, int maxFloor) {
        this.id = id;
        this.maxFloor = maxFloor;
        this.currentFloor = 0;
        this.direction = Direction.IDLE;
        // Min-Heap for processing ascending requests
        this.upQueue = new PriorityQueue<>();
        // Max-Heap for processing descending requests
        this.downQueue = new PriorityQueue<>(Collections.reverseOrder());
        this.upStops = new HashSet<>();
        this.downStops = new HashSet<>();
    }

    public int getId() { return id; }
    public int getCurrentFloor() { return currentFloor; }
    public Direction getDirection() { return direction; }

    public void addHallRequest(HallRequest request) {
        int floor = request.getPickupFloor();
        validateFloor(floor);

        if (floor == currentFloor) {
            openDoor();
            return;
        }

        setInitialDirection(floor);
        addStop(floor, request.getDirection());
    }

    public void addCabinRequest(CabinRequest request) {
        int floor = request.getDestinationFloor();
        validateFloor(floor);

        if (floor == currentFloor) {
            openDoor();
            return;
        }

        setInitialDirection(floor);
        Direction travelDirection = floor > currentFloor ? Direction.UP : Direction.DOWN;
        addStop(floor, travelDirection);
    }

    private void validateFloor(int floor) {
        if (floor < 0 || floor > maxFloor) {
            throw new IllegalArgumentException("Floor must be between 0 and " + maxFloor);
        }
    }

    private void setInitialDirection(int floor) {
        if (direction == Direction.IDLE && floor != currentFloor) {
            direction = floor > currentFloor ? Direction.UP : Direction.DOWN;
        }
    }

    private void addStop(int floor, Direction stopDirection) {
        if (stopDirection == Direction.UP) {
            if (upStops.add(floor)) {
                upQueue.offer(floor);
            }
        } else {
            if (downStops.add(floor)) {
                downQueue.offer(floor);
            }
        }
    }

    public boolean hasRequests() {
        return !upQueue.isEmpty() || !downQueue.isEmpty();
    }

    public void processNextRequest() {
        if (!hasRequests()) {
            direction = Direction.IDLE;
            return;
        }

        if (direction == Direction.UP) {
            if (!upQueue.isEmpty()) {
                currentFloor = upQueue.poll();
                upStops.remove(currentFloor);
                System.out.printf("Elevator %d: Moved UP to Floor %d%n", id, currentFloor);
                openDoor();
            } else {
                int nextFloor = downQueue.poll();
                boolean traveledUp = nextFloor > currentFloor;
                currentFloor = nextFloor;
                downStops.remove(currentFloor);
                direction = Direction.DOWN;
                System.out.printf("Elevator %d: Moved %s to Floor %d and changed direction to DOWN%n",
                        id, traveledUp ? "UP" : "DOWN", currentFloor);
                openDoor();
            }
        } else if (direction == Direction.DOWN) {
            if (!downQueue.isEmpty()) {
                currentFloor = downQueue.poll();
                downStops.remove(currentFloor);
                System.out.printf("Elevator %d: Moved DOWN to Floor %d%n", id, currentFloor);
                openDoor();
            } else {
                int nextFloor = upQueue.poll();
                boolean traveledDown = nextFloor < currentFloor;
                currentFloor = nextFloor;
                upStops.remove(currentFloor);
                direction = Direction.UP;
                System.out.printf("Elevator %d: Moved %s to Floor %d and changed direction to UP%n",
                        id, traveledDown ? "DOWN" : "UP", currentFloor);
                openDoor();
            }
        }
        
        if (!hasRequests()) {
            direction = Direction.IDLE;
        }
    }

    private void openDoor() {
        System.out.printf("Elevator %d: [Door Open] at Floor %d%n", id, currentFloor);
        System.out.printf("Elevator %d: [Door Close] at Floor %d%n", id, currentFloor);
    }
}
