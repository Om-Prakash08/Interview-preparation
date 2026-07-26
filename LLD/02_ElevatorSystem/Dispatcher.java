package elevator;

import java.util.List;

public class Dispatcher {
    private final List<Elevator> elevators;

    public Dispatcher(List<Elevator> elevators) {
        this.elevators = elevators;
    }

    public void dispatch(Request request) {
        Elevator bestElevator = null;
        int minCost = Integer.MAX_VALUE;

        for (Elevator elevator : elevators) {
            int cost = calculateCost(elevator, request);
            if (cost < minCost) {
                minCost = cost;
                bestElevator = elevator;
            }
        }

        if (bestElevator != null) {
            System.out.printf("[Dispatcher] Routing call from Floor %d (%s) to Elevator %d%n",
                    request.getFloor(), request.getDirection(), bestElevator.getId());
            bestElevator.addRequest(request.getFloor(), request.getDirection());
        }
    }

    private int calculateCost(Elevator elevator, Request request) {
        int elevatorFloor = elevator.getCurrentFloor();
        Direction elevatorDir = elevator.getDirection();
        int requestFloor = request.getFloor();
        Direction requestDir = request.getDirection();

        int distance = Math.abs(elevatorFloor - requestFloor);

        if (elevatorDir == Direction.IDLE) {
            return distance; // IDLE elevator gets direct distance score
        }

        if (elevatorDir == Direction.UP) {
            if (requestFloor >= elevatorFloor) {
                if (requestDir == Direction.UP) {
                    return distance; // Best matching case
                } else {
                    return distance + 5; // Moving towards request but in opposite target direction
                }
            } else {
                return distance + 20; // Passed the floor, high penalty
            }
        } else { // Direction.DOWN
            if (requestFloor <= elevatorFloor) {
                if (requestDir == Direction.DOWN) {
                    return distance; // Best matching case
                } else {
                    return distance + 5;
                }
            } else {
                return distance + 20; // Passed the floor, high penalty
            }
        }
    }
}
