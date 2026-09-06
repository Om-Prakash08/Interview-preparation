package elevator;

public class Elevator implements Runnable {
    private final int id;
    private int currentFloor;
    private Direction direction;
    private final boolean[] upRequests;
    private final boolean[] downRequests;
    private final int maxFloor;

    public Elevator(int id, int maxFloor) {
        this.id = id;
        this.maxFloor = maxFloor;
        this.currentFloor = 0;
        this.direction = Direction.IDLE;
        this.upRequests = new boolean[maxFloor + 1];
        this.downRequests = new boolean[maxFloor + 1];
    }

    public int getId()                  { return id; }
    public synchronized int getCurrentFloor() { return currentFloor; }
    public synchronized Direction getDirection() { return direction; }

    public synchronized void addRequest(int floor, Direction reqDir) {
        if (floor < 0 || floor > maxFloor) return;

        if (reqDir == Direction.UP || (reqDir == Direction.IDLE && floor > currentFloor)) {
            upRequests[floor] = true;
        } else if (reqDir == Direction.DOWN || (reqDir == Direction.IDLE && floor < currentFloor)) {
            downRequests[floor] = true;
        } else {
            System.out.printf("Elevator %d is already at floor %d. Door opening...%n", id, floor);
            openDoor();
            return;
        }

        if (direction == Direction.IDLE) {
            direction = (floor > currentFloor) ? Direction.UP : Direction.DOWN;
            notifyAll(); // wake up the run() loop
        }
    }

    private void openDoor() {
        System.out.printf("Elevator %d: [Door Open] at Floor %d%n", id, currentFloor);
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.printf("Elevator %d: [Door Close] at Floor %d%n", id, currentFloor);
    }

    @Override
    public void run() {
        System.out.printf("Elevator %d thread started.%n", id);
        while (!Thread.currentThread().isInterrupted()) {
            synchronized (this) {
                while (direction == Direction.IDLE) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }

            try {
                Thread.sleep(500); // simulate travel time between floors
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            synchronized (this) {
                if (direction == Direction.UP) {
                    currentFloor++;
                    System.out.printf("Elevator %d: Ascending to Floor %d%n", id, currentFloor);

                    boolean shouldStop = upRequests[currentFloor] ||
                            (downRequests[currentFloor] && !hasRequestsAbove(currentFloor));
                    if (shouldStop) {
                        openDoor();
                        upRequests[currentFloor] = false;
                        if (!hasRequestsAbove(currentFloor)) downRequests[currentFloor] = false;
                    }

                    if (hasRequestsAbove(currentFloor))       direction = Direction.UP;
                    else if (hasRequestsBelow(currentFloor))  direction = Direction.DOWN;
                    else                                       direction = Direction.IDLE;

                } else if (direction == Direction.DOWN) {
                    currentFloor--;
                    System.out.printf("Elevator %d: Descending to Floor %d%n", id, currentFloor);

                    boolean shouldStop = downRequests[currentFloor] ||
                            (upRequests[currentFloor] && !hasRequestsBelow(currentFloor));
                    if (shouldStop) {
                        openDoor();
                        downRequests[currentFloor] = false;
                        if (!hasRequestsBelow(currentFloor)) upRequests[currentFloor] = false;
                    }

                    if (hasRequestsBelow(currentFloor))       direction = Direction.DOWN;
                    else if (hasRequestsAbove(currentFloor))  direction = Direction.UP;
                    else                                       direction = Direction.IDLE;
                }
            }
        }
    }

    private boolean hasRequestsAbove(int floor) {
        for (int i = floor + 1; i <= maxFloor; i++)
            if (upRequests[i] || downRequests[i]) return true;
        return false;
    }

    private boolean hasRequestsBelow(int floor) {
        for (int i = floor - 1; i >= 0; i--)
            if (upRequests[i] || downRequests[i]) return true;
        return false;
    }
}
