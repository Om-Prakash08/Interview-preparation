# 14. Snake & Ladder Game — LLD Interview Guide

> **Framework:** Requirements → Entities → Class Design → Implementation → Extensibility

---

## ① Requirements (Clarify First!)

> *"Before I write any code, let me confirm the scope."*

**Functional Requirements:**
- 100-cell board with configurable snakes and ladders
- 2+ players take turns rolling dice and moving their token
- **Snake head** → player slides to snake tail (lower position)
- **Ladder start** → player climbs to ladder end (higher position)
- First player to reach **exactly position 100** wins
- Support **multiple dice** (sum of all dice rolled each turn)
- Track leaderboard — players who reach 100 are ranked in order

**Non-Functional Requirements:**
- **Thread-safe** turns (safe for future multiplayer server mode)
- Configurable board (number of snakes/ladders, board size)
- Recursive jump resolution (snake at tail of another snake, etc.)

**Out of Scope:**
- Graphical board rendering
- Network multiplayer
- Dice rolling animation

---

## ② Entities (Nouns → Classes)

> *"I'll identify the key nouns to define my classes."*

| Entity | Type | Responsibility |
|---|---|---|
| `Dice` | Class | Rolls `n` dice; returns sum in `[n, n×6]` |
| `Snake` | Class | head (int) + tail (int); validates head > tail |
| `Ladder` | Class | start (int) + end (int); validates start < end |
| `Player` | Class | id, name, position (thread-safe) |
| `Board` | Class | Size, Map of snakes, Map of ladders; `getNextPosition()` |
| `Game` | Class | board, dice, player queue, leaderboard; `playTurn()` |

---

## ③ Class Design (Design Patterns)

> *"I'll highlight the design patterns used and why."*

### 🔷 Queue for Turn Management
```
players: Queue<Player>  (circular turn order)
→ Each turn: poll() → play → if not winner: offer() back to queue
→ If winner: add to leaderboard, do NOT offer() back
```
**Why?** Queue naturally models round-robin turn order. When a player wins, they exit the queue.

### 🔷 Recursive Jump Resolution — Board
```java
public int getNextPosition(int currentPos) {
    if (snakes.containsKey(currentPos)) {
        System.out.println("Snake! " + currentPos + " → " + snakes.get(currentPos).getTail());
        return getNextPosition(snakes.get(currentPos).getTail()); // Recursive
    }
    if (ladders.containsKey(currentPos)) {
        System.out.println("Ladder! " + currentPos + " → " + ladders.get(currentPos).getEnd());
        return getNextPosition(ladders.get(currentPos).getEnd()); // Recursive
    }
    return currentPos; // Stable position
}
```
**Why recursion?** If a ladder end lands on a snake head (or vice versa), the resolution chain continues automatically.

### 🔷 Class Skeleton
```java
public class Dice {
    private final int numberOfDice;
    public int roll(); // Random sum: [numDice, numDice × 6]
}

public class Snake {
    private final int head, tail; // head > tail (going down)
}

public class Ladder {
    private final int start, end; // start < end (going up)
}

public class Player {
    private final String id, name;
    private int position; // 0 = start, 100 = win

    @Synchronized public int getPosition();
    @Synchronized public void setPosition(int position);
}

public class Board {
    private final int size;
    private final Map<Integer, Snake>  snakes;
    private final Map<Integer, Ladder> ladders;

    public void addSnake(Snake snake);
    public void addLadder(Ladder ladder);
    public int getNextPosition(int currentPos); // Recursive resolution
}

public class Game {
    private final Board board;
    private final Dice dice;
    private final Queue<Player> players;
    private final List<Player> leaderboard;
    private boolean isGameOver;

    @Synchronized public void playTurn();
    @Synchronized public boolean isGameOver();
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Setup
```java
Board board = new Board(100);

// Add snakes (head > tail = slide DOWN)
board.addSnake(new Snake(14, 7));   // Land on 14 → go to 7
board.addSnake(new Snake(99, 5));   // Land on 99 → go to 5

// Add ladders (start < end = climb UP)
board.addLadder(new Ladder(3, 22));  // Land on 3 → jump to 22
board.addLadder(new Ladder(24, 60)); // Land on 24 → jump to 60

Player alice = new Player("p1", "Alice");
Player bob   = new Player("p2", "Bob");

Game game = new Game(board, new Dice(1), Arrays.asList(alice, bob));
```

### Game Loop
```java
while (!game.isGameOver()) {
    game.playTurn();
}
// playTurn() internal:
// 1. player = players.poll()         ← dequeue front player
// 2. roll = dice.roll()              ← e.g. 6
// 3. newPos = player.position + roll ← 0 + 6 = 6
// 4. if newPos > 100 → skip turn (overshoot, don't move)
// 5. newPos = board.getNextPosition(newPos) ← resolve snakes/ladders
// 6. player.setPosition(newPos)
// 7. if newPos == 100 → leaderboard.add(player) → game over check
// 8. else → players.offer(player)    ← re-enqueue for next turn

System.out.println("Winner: " + game.getLeaderboard().get(0).getName());
```

### Example Turn Trace
```
Alice (pos=0) rolls 3 → pos=3 → Ladder! 3→22 → pos=22
Bob   (pos=0) rolls 14 → pos=14 → Snake! 14→7 → pos=7
Alice (pos=22) rolls 2 → pos=24 → Ladder! 24→60 → pos=60
...
```

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| Multiple dice | `new Dice(2)` — `roll()` already sums N dice |
| Power cards (skip turn) | Add `PowerCard` entity, draw on landing specific cells |
| Save/resume game | Serialize `Queue<Player>` state and board config |
| Web multiplayer | Add `GameSession` wrapper; `playTurn()` called via REST API |
| Undo last move | Store previous position in `Player`; `undoTurn()` in `Game` |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `Game.playTurn()` | `@Synchronized` | Prevent two players taking turns simultaneously |
| `Game.isGameOver()` | `@Synchronized` | Consistent read of game state |
| `Player.getPosition / setPosition` | `@Synchronized` | Safe position updates in multiplayer server |
