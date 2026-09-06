package game;

public class Player {
    private final String id;
    private final String name;
    private int position;

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
        this.position = 0;
    }

    public String getId()   { return id; }
    public String getName() { return name; }

    public synchronized int getPosition()              { return position; }
    public synchronized void setPosition(int position) { this.position = position; }
}
