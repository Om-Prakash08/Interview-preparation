package game;

import java.util.HashMap;
import java.util.Map;

public class Board {
    private final int size;
    private final Map<Integer, Snake> snakes;
    private final Map<Integer, Ladder> ladders;

    public Board(int size) {
        this.size = size;
        this.snakes = new HashMap<>();
        this.ladders = new HashMap<>();
    }

    public int getSize() { return size; }

    public void addSnake(Snake snake)   { snakes.put(snake.getHead(), snake); }
    public void addLadder(Ladder ladder){ ladders.put(ladder.getStart(), ladder); }

    public int getNextPosition(int currentPos) {
        int nextPos = currentPos;
        while (true) {
            if (snakes.containsKey(nextPos)) {
                Snake snake = snakes.get(nextPos);
                System.out.printf("  [Snake Bitten] Slipped from %d down to %d!%n", nextPos, snake.getTail());
                nextPos = snake.getTail();
            } else if (ladders.containsKey(nextPos)) {
                Ladder ladder = ladders.get(nextPos);
                System.out.printf("  [Ladder Climbed] Ascended from %d up to %d!%n", nextPos, ladder.getEnd());
                nextPos = ladder.getEnd();
            } else break;
        }
        return nextPos;
    }
}
