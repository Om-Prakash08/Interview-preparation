package uber;

public class Driver {
    private final String id;
    private final String name;
    private Location location;
    private DriverStatus status;

    public Driver(String id, String name, Location location) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.status = DriverStatus.OFFLINE;
    }

    public String getId()   { return id; }
    public String getName() { return name; }

    public synchronized Location getLocation()              { return location; }
    public synchronized void setLocation(Location location) { this.location = location; }
    public synchronized DriverStatus getStatus()            { return status; }
    public synchronized void setStatus(DriverStatus status) { this.status = status; }
}
