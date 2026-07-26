package game;

import lombok.Getter;
import lombok.Synchronized;

public class Player {
    @Getter
    private final String id;
    @Getter
    private final String name;
    private int position;

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
        this.position = 0; // Starts outside the board
    }

    @Synchronized
    public int getPosition() {
        return position;
    }

    @Synchronized
    public void setPosition(int position) {
        this.position = position;
    }
}
