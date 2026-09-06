package booking;

import java.util.ArrayList;
import java.util.List;

public class Cinema {
    private final String name;
    private final String city;
    private final List<Show> shows;

    public Cinema(String name, String city) {
        this.name = name;
        this.city = city;
        this.shows = new ArrayList<>();
    }

    public String getName()     { return name; }
    public String getCity()     { return city; }
    public List<Show> getShows(){ return shows; }

    public void addShow(Show show) { shows.add(show); }
}
