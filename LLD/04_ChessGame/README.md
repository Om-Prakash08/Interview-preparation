# 04. Chess Game (Java LLD Solution)

This folder contains a complete Java implementation of an Object-Oriented Chess Game.

Below is the **Complete Class Skeleton and API Design** so you can understand the entire system architecture, fields, and method signatures without looking at the source code.

---

## 1. Class Diagram / Architecture Skeleton

### Enums & Players
```java
public enum Color { WHITE, BLACK }

@Getter
@AllArgsConstructor
public class Player {
    private final String name;
    private final Color color;
}
```

### Board Layout Elements
```java
@Getter
public class Box {
    private final int x;
    private final int y;
    @Setter private Piece piece;

    public Box(int x, int y);
}

@Getter
public abstract class Piece {
    private final Color color;
    @Setter private boolean killed = false;

    public abstract boolean canMove(Board board, Box start, Box end);
    public abstract String getSymbol(); // WK, BK, WN, BN etc.
}

// Subclasses implementing specific movements:
class King extends Piece { ... }
class Queen extends Piece { ... }
class Rook extends Piece { ... }
class Bishop extends Piece { ... }
class Knight extends Piece { ... }
class Pawn extends Piece { ... }
```

### Game Core Coordinates & Transactions
```java
public class Board {
    private final Box[][] boxes = new Box[8][8];

    public Box getBox(int x, int y);
    public void resetBoard(); // Positions standard chess pieces
}

@Getter
public class Move {
    private final Player player;
    private final Box start;
    private final Box end;
    private final Piece pieceMoved;
    private final Piece pieceKilled;
}

@Getter
public class Game {
    public enum GameStatus { ACTIVE, WHITE_WIN, BLACK_WIN, FORFEIT, STALEMATE }

    private final Board board;
    private final Player[] players;
    private Player currentTurn;
    private GameStatus status;
    private final List<Move> movesPlayed;

    @Synchronized public boolean playerMove(Player player, int startX, int startY, int endX, int endY); // Validates & applies move
    public void printBoardState(); // Prints board on console
}
```

---

## 2. Core Workflow & Usage

Here is how a chess game turn sequence is run:

```java
Player p1 = new Player("Alice", Color.WHITE);
Player p2 = new Player("Bob", Color.BLACK);

Game game = new Game(p1, p2);
game.printBoardState();

// White player makes first move
game.playerMove(p1, 1, 3, 3, 3); // Alice moves White Pawn from (1,3) to (3,3)

// Black player responds
game.playerMove(p2, 7, 1, 5, 2); // Bob moves Black Knight from (7,1) to (5,2)
```

---

## 3. Concurrency & Thread-Safety Details
- **Turn Locking**: The `playerMove` method is synchronized (`@Synchronized`) at the `Game` level to ensure that moves are executed in sequence, preventing concurrent request races in online multiplayer chess.
- **Killed Status**: Chess piece state mutations (`killed` status) are handled safely during board updates.
