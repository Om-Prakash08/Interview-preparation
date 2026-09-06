package elevator;

import java.util.Collections;
import java.util.PriorityQueue;

public class Elevator {
    private final int id;
    private int currentFloor;
    private Direction direction;
    private final PriorityQueue<Integer> upQueue;
    private final PriorityQueue<Integer> downQueue;
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
    }

    public int getId() { return id; }
    public int getCurrentFloor() { return currentFloor; }
    public Direction getDirection() { return direction; }

    public void addRequest(int floor, Direction reqDir) {
        if (floor < 0 || floor > maxFloor) return;

        if (floor == currentFloor && direction == Direction.IDLE) {
            System.out.printf("Elevator %d is already at floor %d. Door opening...%n", id, floor);
            openDoor();
            return;
        }

        if (direction == Direction.IDLE) {
            direction = (floor > currentFloor) ? Direction.UP : Direction.DOWN;
        }

        // Add to appropriate queue
        if (reqDir == Direction.UP || (reqDir == Direction.IDLE && floor > currentFloor)) {
            if (!upQueue.contains(floor)) upQueue.offer(floor);
        } else {
            if (!downQueue.contains(floor)) downQueue.offer(floor);
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
                System.out.printf("Elevator %d: Moved UP to Floor %d%n", id, currentFloor);
                openDoor();
            } else {
                direction = Direction.DOWN;
                currentFloor = downQueue.poll();
                System.out.printf("Elevator %d: Changed direction and moved DOWN to Floor %d%n", id, currentFloor);
                openDoor();
            }
        } else if (direction == Direction.DOWN) {
            if (!downQueue.isEmpty()) {
                currentFloor = downQueue.poll();
                System.out.printf("Elevator %d: Moved DOWN to Floor %d%n", id, currentFloor);
                openDoor();
            } else {
                direction = Direction.UP;
                currentFloor = upQueue.poll();
                System.out.printf("Elevator %d: Changed direction and moved UP to Floor %d%n", id, currentFloor);
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
