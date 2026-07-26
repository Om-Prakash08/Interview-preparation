package uber;

import lombok.Getter;
import lombok.Synchronized;

@Getter
public class Ride {
    private final String rideId;
    private final Rider rider;
    private final Driver driver;
    private final Location pickup;
    private final Location destination;
    private final double fare;
    private RideStatus status;

    public Ride(String rideId, Rider rider, Driver driver, Location pickup, Location destination, double fare) {
        this.rideId = rideId;
        this.rider = rider;
        this.driver = driver;
        this.pickup = pickup;
        this.destination = destination;
        this.fare = fare;
        this.status = RideStatus.REQUESTED;
    }

    @Synchronized
    public RideStatus getStatus() {
        return status;
    }

    @Synchronized
    public void setStatus(RideStatus status) {
        this.status = status;
    }
}
