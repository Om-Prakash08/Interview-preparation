package booking;

import lombok.Getter;
import java.util.ArrayList;
import java.util.List;

@Getter
public class Cinema {
    private final String name;
    private final String city;
    private final List<Show> shows;

    public Cinema(String name, String city) {
        this.name = name;
        this.city = city;
        this.shows = new ArrayList<>();
    }

    public void addShow(Show show) {
        shows.add(show);
    }
}
