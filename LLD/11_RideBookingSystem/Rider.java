package uber;

import lombok.Getter;
import lombok.Synchronized;

public class Rider {
    @Getter
    private final String id;
    @Getter
    private final String name;
    private Location location;

    public Rider(String id, String name, Location location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    @Synchronized
    public Location getLocation() {
        return location;
    }

    @Synchronized
    public void setLocation(Location location) {
        this.location = location;
    }
}
