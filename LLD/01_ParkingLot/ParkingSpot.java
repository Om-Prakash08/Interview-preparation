package parkinglot;

import lombok.Getter;
import lombok.Synchronized;

public class ParkingSpot {
    @Getter
    private final String spotId;
    @Getter
    private final ParkingSpotType type;
    private boolean isFree;
    private Vehicle vehicle;

    public ParkingSpot(String spotId, ParkingSpotType type) {
        this.spotId = spotId;
        this.type = type;
        this.isFree = true;
        this.vehicle = null;
    }

    @Synchronized
    public boolean isFree() {
        return isFree;
    }

    @Synchronized
    public Vehicle getVehicle() {
        return vehicle;
    }

    @Synchronized
    public boolean park(Vehicle vehicle) {
        if (!isFree) {
            return false;
        }
        this.vehicle = vehicle;
        this.isFree = false;
        return true;
    }

    @Synchronized
    public void removeVehicle() {
        this.vehicle = null;
        this.isFree = true;
    }
}
