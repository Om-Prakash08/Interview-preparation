package booking;

public class Movie {
    private final String id;
    private final String title;
    private final String genre;
    private final int durationMinutes;

    public Movie(String id, String title, String genre, int durationMinutes) {
        this.id = id;
        this.title = title;
        this.genre = genre;
        this.durationMinutes = durationMinutes;
    }

    public String getId()           { return id; }
    public String getTitle()        { return title; }
    public String getGenre()        { return genre; }
    public int getDurationMinutes() { return durationMinutes; }
}
