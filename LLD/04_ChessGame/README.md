# 04. Chess Game — LLD Interview Guide

> **Framework:** Requirements → Entities → Class Design → Implementation → Extensibility

---

## ① Requirements (Clarify First!)

> *"Before I write any code, let me confirm the scope."*

**Functional Requirements:**
- 2 players: one `WHITE`, one `BLACK`
- 8×8 `Board` initialized with all standard pieces
- Players alternate turns; each turn validates and applies a `Move`
- Validate **piece-specific movement rules** (King, Queen, Rook, Bishop, Knight, Pawn)
- Detect **check**, **checkmate**, and **stalemate** conditions
- Track complete **move history**

**Non-Functional Requirements:**
- **Thread-safe** turns (concurrent online multiplayer)
- Extensible for new piece types or custom rules

**Out of Scope:**
- Castling, en passant (unless interviewer asks)
- AI opponent / chess engine
- Online networking / sockets

---

## ② Entities (Nouns → Classes)

> *"I'll identify the key nouns to define my classes."*

| Entity | Type | Responsibility |
|---|---|---|
| `Color` | Enum | `WHITE, BLACK` |
| `Player` | Class | Name + color |
| `Box` | Class | A single cell on the board (x, y, piece) |
| `Board` | Class | 8×8 grid of `Box`; initializes pieces |
| `Piece` | Abstract | Color + `killed` flag + `canMove()` contract |
| `King/Queen/Rook/Bishop/Knight/Pawn` | Concrete | Specific movement logic |
| `Move` | Class | player, start Box, end Box, pieceMoved, pieceKilled |
| `Game` | Class | Orchestrates turns, tracks status + move history |

---

## ③ Class Design (Design Patterns)

> *"I'll highlight the design patterns used and why."*

### 🔷 Template Method — Piece Hierarchy
```
Piece (abstract)
    ├── canMove(Board, Box start, Box end) → abstract
    ├── getSymbol() → abstract
    └── Subclasses: King, Queen, Rook, Bishop, Knight, Pawn
```
**Why?** Each piece has unique movement logic but shares the same interface. Adding a new piece type (e.g. custom fairy chess piece) only requires extending `Piece`.

### 🔷 Command Pattern — Move as Object
```
Move {
    Player player
    Box start, end
    Piece pieceMoved
    Piece pieceKilled  ← null if no capture
}
```
**Why?** Moves stored in a list enable full move history, undo support, and replay functionality.

### 🔷 Class Skeleton
```java
public enum Color { WHITE, BLACK }

public class Player {
    private final String name;
    private final Color color;
}

public class Box {
    private final int x, y;
    private Piece piece;
}

public abstract class Piece {
    private final Color color;
    private boolean killed = false;

    public abstract boolean canMove(Board board, Box start, Box end);
    public abstract String getSymbol(); // WK, BQ, WN, BP, etc.
}

public class Board {
    private final Box[][] boxes = new Box[8][8];

    public Box getBox(int x, int y);
    public void resetBoard(); // Places all 32 standard pieces
}

public class Move {
    private final Player player;
    private final Box start, end;
    private final Piece pieceMoved;
    private final Piece pieceKilled; // null if no capture
}

public class Game {
    public enum GameStatus { ACTIVE, WHITE_WIN, BLACK_WIN, FORFEIT, STALEMATE }

    private final Board board;
    private final Player[] players;  // [0]=WHITE, [1]=BLACK
    private Player currentTurn;
    private GameStatus status;
    private final List<Move> movesPlayed;

    @Synchronized public boolean playerMove(Player player, int startX, int startY, int endX, int endY);
    public void printBoardState();
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Game Setup
```java
Player alice = new Player("Alice", Color.WHITE);
Player bob   = new Player("Bob",   Color.BLACK);

Game game = new Game(alice, bob);
game.printBoardState();
// Standard board layout printed; Alice (WHITE) goes first
```

### Turn Execution (inside `playerMove`)
```java
game.playerMove(alice, 1, 3, 3, 3); // Alice moves White Pawn from (1,3) → (3,3)
// Internal steps:
// 1. Verify it's alice's turn
// 2. Get start = board.getBox(1,3), end = board.getBox(3,3)
// 3. Validate: start.getPiece() != null && piece.getColor() == alice.getColor()
// 4. Call piece.canMove(board, start, end) → Pawn validates 2-step advance from start row
// 5. Kill end.getPiece() if enemy piece present
// 6. Move piece to end box, clear start box
// 7. Record Move in movesPlayed
// 8. Check for checkmate / stalemate → update GameStatus
// 9. Flip currentTurn to bob

game.playerMove(bob, 7, 1, 5, 2); // Bob moves Black Knight
```

### Win Detection
```
After each move, scan if opponent's King is in check:
  → All opponent pieces' canMove(board, theirPos, kingPos) == true → CHECK
  → No legal move available for opponent → CHECKMATE → game ends
```

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| Add castling / en passant | Add special-case logic in `King.canMove` and `Pawn.canMove` |
| Undo last move | Reverse last entry in `movesPlayed` list |
| Chess960 (random start) | Add `randomizeBoard()` in `Board` |
| AI opponent | Implement `AIPlayer extends Player`, minimax in `playerMove` |
| Timed game (clock) | Add `ChessClock` entity observing `movesPlayed` list |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `Game.playerMove` | `@Synchronized` | Prevent concurrent moves (online multiplayer) |
| `Piece.killed` | Setter called inside synchronized block | Prevent stale read of piece status |
