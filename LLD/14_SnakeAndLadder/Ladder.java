package game;

public class Ladder {
    private final int start;
    private final int end;

    public Ladder(int start, int end) {
        if (start >= end) throw new IllegalArgumentException("Ladder end must be at a higher cell than start");
        this.start = start;
        this.end = end;
    }

    public int getStart() { return start; }
    public int getEnd()   { return end; }
}
