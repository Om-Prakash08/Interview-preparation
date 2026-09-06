package uber;

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

    public String getRideId()         { return rideId; }
    public Rider getRider()           { return rider; }
    public Driver getDriver()         { return driver; }
    public Location getPickup()       { return pickup; }
    public Location getDestination()  { return destination; }
    public double getFare()           { return fare; }

    public synchronized RideStatus getStatus()              { return status; }
    public synchronized void setStatus(RideStatus status)   { this.status = status; }
}
