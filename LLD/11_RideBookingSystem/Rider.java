package uber;

public class Rider {
    private final String id;
    private final String name;
    private Location location;

    public Rider(String id, String name, Location location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    public String getId()   { return id; }
    public String getName() { return name; }

    public synchronized Location getLocation()              { return location; }
    public synchronized void setLocation(Location location) { this.location = location; }
}
