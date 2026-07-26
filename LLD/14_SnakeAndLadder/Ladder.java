package game;

import lombok.Getter;

@Getter
public class Ladder {
    private final int start;
    private final int end;

    public Ladder(int start, int end) {
        if (start >= end) {
            throw new IllegalArgumentException("Ladder end must be at a higher cell than start");
        }
        this.start = start;
        this.end = end;
    }
}
