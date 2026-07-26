package game;

import lombok.Getter;

@Getter
public class Snake {
    private final int head;
    private final int tail;

    public Snake(int head, int tail) {
        if (head <= tail) {
            throw new IllegalArgumentException("Snake head must be at a higher cell than tail");
        }
        this.head = head;
        this.tail = tail;
    }
}
