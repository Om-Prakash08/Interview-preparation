package uber;

import lombok.Getter;
import lombok.Synchronized;

public class Driver {
    @Getter
    private final String id;
    @Getter
    private final String name;
    private Location location;
    private DriverStatus status;

    public Driver(String id, String name, Location location) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.status = DriverStatus.OFFLINE;
    }

    @Synchronized
    public Location getLocation() {
        return location;
    }

    @Synchronized
    public void setLocation(Location location) {
        this.location = location;
    }

    @Synchronized
    public DriverStatus getStatus() {
        return status;
    }

    @Synchronized
    public void setStatus(DriverStatus status) {
        this.status = status;
    }
}
