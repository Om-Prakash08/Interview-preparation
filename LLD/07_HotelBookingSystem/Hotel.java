package hotel;

import java.util.ArrayList;
import java.util.List;

public class Hotel {
    private final String name;
    private final String location;
    private final List<Room> rooms;

    public Hotel(String name, String location) {
        this.name = name;
        this.location = location;
        this.rooms = new ArrayList<>();
    }

    public String getName()     { return name; }
    public String getLocation() { return location; }
    public List<Room> getRooms(){ return rooms; }

    public void addRoom(Room room) { rooms.add(room); }
}
