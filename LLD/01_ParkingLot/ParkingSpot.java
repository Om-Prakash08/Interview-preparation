package parkinglot;

public abstract class ParkingSpot {
    public abstract boolean canFit(VehicleType vehicleType);
    private final String spotId;
    private final ParkingSpotType type;
    private boolean isFree;
    private Vehicle vehicle;

    protected ParkingSpot(String spotId, ParkingSpotType type) {
        this.spotId = spotId;
        this.type = type;
        this.isFree = true;
        this.vehicle = null;
    }

    public String getSpotId() { return spotId; }
    public ParkingSpotType getType() { return type; }

    public synchronized boolean isFree() {
        return isFree;
    }

    public synchronized Vehicle getVehicle() {
        return vehicle;
    }

    public synchronized boolean park(Vehicle vehicle) {
        if (!isFree) {
            return false;
        }
        this.vehicle = vehicle;
        this.isFree = false;
        return true;
    }

    public synchronized void removeVehicle() {
        this.vehicle = null;
        this.isFree = true;
    }
}

class SmallSpot extends ParkingSpot {
    public SmallSpot(String spotId) { super(spotId, ParkingSpotType.SMALL); }

    @Override
    public boolean canFit(VehicleType vehicleType) {
        return vehicleType == VehicleType.MOTORCYCLE;
    }
}

class MediumSpot extends ParkingSpot {
    public MediumSpot(String spotId) { super(spotId, ParkingSpotType.MEDIUM); }

    @Override
    public boolean canFit(VehicleType vehicleType) {
        return vehicleType == VehicleType.MOTORCYCLE || vehicleType == VehicleType.CAR;
    }
}

class LargeSpot extends ParkingSpot {
    public LargeSpot(String spotId) { super(spotId, ParkingSpotType.LARGE); }

    @Override
    public boolean canFit(VehicleType vehicleType) {
        return true; // fits all vehicle types
    }
}
