package parkinglot;

import lombok.Getter;
import lombok.Synchronized;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Level {
    @Getter
    private final String levelId;
    private final List<ParkingSpot> spots;

    public Level(String levelId, int numSpots) {
        this.levelId = levelId;
        this.spots = new ArrayList<>(numSpots);
        initializeSpots(numSpots);
    }

    private void initializeSpots(int numSpots) {
        int motorCycleSpots = numSpots / 5;
        int compactSpots = (numSpots * 3) / 5;
        int largeSpots = numSpots - motorCycleSpots - compactSpots;

        for (int i = 1; i <= motorCycleSpots; i++) {
            spots.add(new ParkingSpot(levelId + "-M" + i, ParkingSpotType.MOTORCYCLE));
        }
        for (int i = 1; i <= compactSpots; i++) {
            spots.add(new ParkingSpot(levelId + "-C" + i, ParkingSpotType.COMPACT));
        }
        for (int i = 1; i <= largeSpots; i++) {
            spots.add(new ParkingSpot(levelId + "-L" + i, ParkingSpotType.LARGE));
        }
    }

    public List<ParkingSpot> getSpots() {
        return Collections.unmodifiableList(spots);
    }

    @Synchronized
    public ParkingSpot parkVehicle(Vehicle vehicle) {
        for (ParkingSpot spot : spots) {
            if (spot.isFree() && canFit(vehicle.getType(), spot.getType())) {
                if (spot.park(vehicle)) {
                    return spot;
                }
            }
        }
        return null;
    }

    @Synchronized
    public void freeSpot(ParkingSpot spot) {
        spot.removeVehicle();
    }

    private boolean canFit(VehicleType vehicleType, ParkingSpotType spotType) {
        switch (vehicleType) {
            case MOTORCYCLE:
                return true;
            case CAR:
                return spotType == ParkingSpotType.COMPACT || spotType == ParkingSpotType.LARGE;
            case TRUCK:
            case VAN:
                return spotType == ParkingSpotType.LARGE;
            default:
                return false;
        }
    }
}
