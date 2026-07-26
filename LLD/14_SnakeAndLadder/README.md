# 14. Snake & Ladder Game (Java LLD Solution)

This folder contains a complete Java implementation of a Snake & Ladder Game.

Below is the **Complete Class Skeleton and API Design** so you can understand the entire system architecture, fields, and method signatures without looking at the source code.

---

## 1. Class Diagram / Architecture Skeleton

### Utility & Layout Elements
```java
public class Dice {
    public Dice(int numberOfDice);
    public int roll(); // Returns random sum in range [numDice, numDice * 6]
}

@Getter
public class Snake {
    private final int head;
    private final int tail;

    public Snake(int head, int tail); // Validates head > tail
}

@Getter
public class Ladder {
    private final int start;
    private final int end;

    public Ladder(int start, int end); // Validates start < end
}
```

### Players & Board Configuration
```java
public class Player {
    @Getter private final String id;
    @Getter private final String name;
    private int position;

    public Player(String id, String name);
    @Synchronized public int getPosition();
    @Synchronized public void setPosition(int position);
}

public class Board {
    @Getter private final int size;
    private final Map<Integer, Snake> snakes;
    private final Map<Integer, Ladder> ladders;

    public Board(int size);
    public void addSnake(Snake snake);
    public void addLadder(Ladder ladder);
    public int getNextPosition(int currentPos); // Iterates snakes and ladders recursively until stable
}
```

### Game Core Context & Queue
```java
public class Game {
    private final Board board;
    private final Dice dice;
    private final Queue<Player> players;
    @Getter private final List<Player> leaderboard;
    private boolean isGameOver;

    public Game(Board board, Dice dice, List<Player> playerList);

    @Synchronized public void playTurn(); // Rolls dice, updates position, checks winner, shifts turn
    @Synchronized public boolean isGameOver();
}
```

---

## 2. Core Workflow & Usage

Here is how the game state loop is coordinated:

```java
// 1. Setup board
Board board = new Board(100);
board.addSnake(new Snake(14, 7));
board.addSnake(new Snake(99, 5));
board.addLadder(new Ladder(3, 22));
board.addLadder(new Ladder(24, 60));

// 2. Setup players
Player alice = new Player("p1", "Alice");
Player bob = new Player("p2", "Bob");
Game game = new Game(board, new Dice(1), Arrays.asList(alice, bob));

// 3. Play turns until game finishes
while (!game.isGameOver()) {
    game.playTurn();
}
System.out.println("Winner: " + game.getLeaderboard().get(0).getName());
```

---

## 3. Concurrency & Thread-Safety Details
- **Turn Sequence Safety**: The `playTurn` method in `Game` is fully synchronized (`@Synchronized`), preventing race conditions in multiplayer environments (e.g. players rolling dice out of turn order).
- **Atomic Position Tracking**: The player position is managed with synchronized getters and setters (`@Synchronized`), ensuring accurate rendering of positions during telemetry queries.
- **Stable Board Resolution**: The recursive calculation of jumps (snakes and ladders) in `Board` is pure, which keeps the resolution deterministic and safe from memory inconsistencies.
