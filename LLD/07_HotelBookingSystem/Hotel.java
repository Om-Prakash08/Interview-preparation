package hotel;

import lombok.Getter;
import java.util.ArrayList;
import java.util.List;

@Getter
public class Hotel {
    private final String name;
    private final String location;
    private final List<Room> rooms;

    public Hotel(String name, String location) {
        this.name = name;
        this.location = location;
        this.rooms = new ArrayList<>();
    }

    public void addRoom(Room room) {
        rooms.add(room);
    }
}
