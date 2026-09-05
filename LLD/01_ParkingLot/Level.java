package parkinglot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Level {
    private final String levelId;
    private final List<ParkingSpot> spots;

    public Level(String levelId, int numSpots) {
        this.levelId = levelId;
        this.spots = new ArrayList<>(numSpots);
        initializeSpots(numSpots);
    }

    public String getLevelId() { return levelId; }

    public List<ParkingSpot> getSpots() {
        return Collections.unmodifiableList(spots);
    }

    private void initializeSpots(int numSpots) {
        int smallSpots = numSpots / 5;
        int mediumSpots = (numSpots * 3) / 5;
        int largeSpots = numSpots - smallSpots - mediumSpots;

        for (int i = 1; i <= smallSpots; i++) spots.add(new SmallSpot(levelId + "-S" + i));
        for (int i = 1; i <= mediumSpots; i++) spots.add(new MediumSpot(levelId + "-M" + i));
        for (int i = 1; i <= largeSpots; i++) spots.add(new LargeSpot(levelId + "-L" + i));
    }

    public synchronized ParkingSpot parkVehicle(Vehicle vehicle) {
        for (ParkingSpot spot : spots) {
            if (spot.isFree() && spot.canFit(vehicle.getType())) {
                if (spot.park(vehicle)) return spot;
            }
        }
        return null;
    }

    public synchronized void freeSpot(ParkingSpot spot) {
        spot.removeVehicle();
    }

}
