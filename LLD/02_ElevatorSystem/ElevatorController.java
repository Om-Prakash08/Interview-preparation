package elevator;

import java.util.List;

public class ElevatorController {
    private final List<Elevator> elevators;

    public ElevatorController(List<Elevator> elevators) {
        this.elevators = elevators;
    }

    public void requestElevator(HallRequest request) {
        Elevator bestElevator = selectBestElevator(request);

        if (bestElevator != null) {
            System.out.printf("[ElevatorController] Routing call from Floor %d (%s) to Elevator %d%n",
                    request.getPickupFloor(), request.getDirection(), bestElevator.getId());
            bestElevator.addHallRequest(request);
        }
    }

    private Elevator selectBestElevator(HallRequest request) {
        // Priority 1: Elevators moving toward the floor in the right direction
        Elevator bestElevator = findMovingToward(request);
        if (bestElevator != null) {
            return bestElevator;
        }

        bestElevator = findNearestIdle(request.getPickupFloor());
        if (bestElevator != null) {
            return bestElevator;
        }

        return findNearest(request.getPickupFloor());
    }

    private Elevator findMovingToward(HallRequest request) {
        Elevator nearest = null;
        int minDistance = Integer.MAX_VALUE;

        for (Elevator elevator : elevators) {
            if (elevator.getDirection() != request.getDirection()) {
                continue;
            }

            int elevatorFloor = elevator.getCurrentFloor();
            int pickupFloor = request.getPickupFloor();
            boolean hasPassedPickup =
                    (request.getDirection() == Direction.UP && elevatorFloor > pickupFloor)
                    || (request.getDirection() == Direction.DOWN && elevatorFloor < pickupFloor);

            if (hasPassedPickup) {
                continue;
            }

            int distance = Math.abs(elevatorFloor - pickupFloor);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = elevator;
            }
        }

        return nearest;
    }

    private Elevator findNearestIdle(int pickupFloor) {
        Elevator nearest = null;
        int minDistance = Integer.MAX_VALUE;

        for (Elevator elevator : elevators) {
            if (elevator.getDirection() != Direction.IDLE) {
                continue;
            }

            int distance = Math.abs(elevator.getCurrentFloor() - pickupFloor);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = elevator;
            }
        }

        return nearest;
    }

    private Elevator findNearest(int pickupFloor) {
        Elevator nearest = null;
        int minDistance = Integer.MAX_VALUE;

        for (Elevator elevator : elevators) {
            int distance = Math.abs(elevator.getCurrentFloor() - pickupFloor);
            if (distance < minDistance) {
                minDistance = distance;
                nearest = elevator;
            }
        }

        return nearest;
    }
}
