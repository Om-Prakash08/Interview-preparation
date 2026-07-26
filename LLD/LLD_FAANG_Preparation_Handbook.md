# LLD FAANG Interview Preparation Handbook

> **Your complete interview-day playbook.** Open this file before any LLD interview. Read the Universal Framework first, then navigate to your question.

---

## Table of Contents
- [Universal Interview Framework](#universal-interview-framework)
- [30-Minute Interview Timeline](#30-minute-interview-timeline)
- [FAANG Scoring Rubric](#faang-scoring-rubric)
- [Common Traps to Avoid](#common-traps-to-avoid)
- [Questions Index](#questions-index)

---

## Universal Interview Framework

### The 5-Step Approach (Use This Every Time)

**Step 1 — Clarify & Scope (5 min)**
- Never start coding without asking at least 3 clarifying questions.
- Frame them around: *Who are the actors? What are the core actions? What are the constraints?*
- Say: *"Before I begin, let me ask a few clarifying questions to make sure I'm solving the right problem."*

**Step 2 — Requirements (2 min)**
- Write Functional and Non-Functional requirements on the whiteboard.
- Always mention: **Concurrency, Scalability, Extensibility** under Non-Functional.
- Say: *"Based on your answers, here are the requirements I'll design for."*

**Step 3 — Identify Entities & Relationships (5 min)**
- Identify the core nouns (entities) and verbs (behaviors).
- Draw a quick class relationship diagram: solid lines (HAS-A), arrows (IS-A).
- Say: *"Let me identify the core entities and how they relate to each other."*

**Step 4 — Design & Code (15 min)**
- Start with interfaces and abstract classes first. Fill in concrete classes.
- Annotate with Lombok where applicable (`@Getter`, `@Synchronized`).
- Explain your design pattern choice explicitly — don't make the interviewer guess.
- Say: *"I'm applying the Strategy Pattern here because..."*

**Step 5 — Discuss Concurrency & Follow-ups (3 min)**
- Proactively surface concurrency bottlenecks before being asked.
- Propose your locking strategy and justify the granularity.
- Say: *"Now, if we consider a multi-threaded environment, the key race condition is..."*

---

## 30-Minute Interview Timeline

```
00:00 - 05:00  |  Ask clarifying questions. Write requirements on board.
05:00 - 10:00  |  Draw class diagram. Identify entities, relationships, patterns.
10:00 - 25:00  |  Code the solution. Talk through every decision out loud.
25:00 - 28:00  |  Walk through a usage example (like the Demo flow).
28:00 - 30:00  |  Proactively discuss concurrency and follow-up scaling questions.
```

> **Golden Rule**: Interviewers want to see *how you think*, not just what you code. Narrate every decision.

---

## FAANG Scoring Rubric

| Dimension | What They Look For |
|---|---|
| **Problem Decomposition** | Can you break the problem into clean, manageable entities? |
| **OOP Principles** | SOLID, encapsulation, inheritance, polymorphism applied correctly |
| **Design Patterns** | Do you *choose* patterns intentionally and explain *why*? |
| **API Design** | Are method signatures clean, intuitive, and minimal? |
| **Concurrency Awareness** | Do you identify race conditions and propose appropriate locks? |
| **Extensibility** | Is the design Open/Closed? Can new features be added without rewrites? |
| **Communication** | Do you think out loud and guide the interviewer through your reasoning? |

---

## Common Traps to Avoid

| Trap | What to Do Instead |
|---|---|
| Jumping straight to coding | Always clarify and sketch a class diagram first |
| Over-engineering from the start | Start simple, then add extensibility |
| Using `synchronized` on everything | Justify lock granularity — too coarse kills performance |
| Forgetting enums for constants | Enums make intent clear and prevent invalid states |
| Public mutable fields | Always use private fields with controlled access |
| Singleton without thread safety | Always double-check or use `@Synchronized` on `getInstance()` |
| Not separating data models from logic | Keep entities dumb (data) and services smart (logic) |
| Forgetting to mention time complexity | Always state O(1), O(n), O(log n) for core operations |

---

## Questions Index

1. [Parking Lot System](#1-parking-lot-system)
2. [Elevator Control System](#2-elevator-control-system)
3. [Library Management System](#3-library-management-system)
4. [Chess Game](#4-chess-game)
5. [Vending Machine](#5-vending-machine)
6. [ATM Machine](#6-atm-machine)
7. [Hotel Booking System](#7-hotel-booking-system)
8. [LRU Cache](#8-lru-cache)
9. [Logger Framework](#9-logger-framework)
10. [Splitwise Bill Splitting](#10-splitwise-bill-splitting)
11. [Ride Booking System (mini Uber)](#11-ride-booking-system-mini-uber)
12. [Movie Ticket Booking (mini BookMyShow)](#12-movie-ticket-booking-mini-bookmyshow)
13. [Online Shopping Cart](#13-online-shopping-cart)
14. [Snake & Ladder Game](#14-snake--ladder-game)
15. [File System](#15-file-system)

---

## 1. Parking Lot System

### Clarifying Questions to Ask
- "Does the parking lot have multiple levels and multiple entrance/exit gates?"
- "What types of vehicles do we support?" → Motorcycle, Car, Truck, Van
- "Are there different spot types that match vehicle sizes?" → Yes: Motorcycle, Compact, Large
- "How is pricing calculated?" → Hourly rates, potentially varying by vehicle type
- "Should we handle ticketing and payments at the exit gate?"
- "Is the system for a single city or a distributed multi-location lot?"

### System Requirements
- **Functional**:
  - Support multi-level parking with configurable spots per level.
  - Auto-allocate the nearest available compatible spot when a vehicle enters.
  - Generate an entry ticket with timestamp and spot information.
  - Calculate fee, accept payment (multiple methods), and free the spot at exit.
- **Non-Functional**:
  - **Concurrency**: Multiple entry gates handle simultaneous arrivals — no double-booking the same spot.
  - **Extensibility**: New vehicle types, spot types, and payment strategies must be addable without core changes.

### Core Class Design
- **Enums**: `VehicleType` (MOTORCYCLE, CAR, TRUCK, VAN), `ParkingSpotType` (MOTORCYCLE, COMPACT, LARGE)
- **Entities**: `ParkingLot` (Singleton), `Level`, `ParkingSpot`, `Vehicle` (Abstract + subclasses), `Ticket`
- **Strategies**: `FeeCalculator` (Interface), `HourlyFeeCalculator`, `PaymentStrategy` (Interface), `CreditCardPayment`, `MobileWalletPayment`

### Design Patterns to Highlight
- **Singleton**: `ParkingLot` — one physical lot, one instance.
- **Strategy**: `FeeCalculator` and `PaymentStrategy` — swap pricing or payment rules without modifying the core.
- **Factory**: Creating `Vehicle` subclasses based on vehicle type at the gate.

### Complexity Analysis
| Operation | Time Complexity | Notes |
|---|---|---|
| Issue Ticket | O(L × S) | L = levels, S = spots per level. Linear scan |
| Release Vehicle | O(1) | Direct spot reference stored in ticket |
| Payment Processing | O(1) | Delegate to strategy |

### Concurrency Discussion
- **Bottleneck**: Two cars entering simultaneously on Level 1. Both threads scan, both see Spot-5 as free, both try to park → double-booking.
- **Solution**: Synchronize `parkVehicle(Vehicle)` inside `Level` class. The write lock checks availability and claims the spot atomically within a single critical section.
- **Interview Point**: Lock at the `Level` granularity — not the entire `ParkingLot`. This allows Gate-1 and Gate-2 to book spots on Level-1 vs Level-2 in parallel.

### Follow-Up / Scaling Questions
- *"How would you handle 1000 concurrent vehicles?"* → Shard levels, use `ReentrantLock` per level instead of synchronized, consider optimistic locking.
- *"How would you add reservation support?"* → Add a `ReservationService` with time-bounded pre-booking using a scheduled timeout.
- *"How would you integrate license plate recognition?"* → Add a `VehicleRecognitionService` (Strategy) at entry gates.

### Common Mistakes to Avoid
- Don't put all logic in `ParkingLot` — violates SRP.
- Don't lock the entire `ParkingLot` for spot allocation — too coarse, kills throughput.
- Don't forget to handle the case where no compatible spot is available.

---

## 2. Elevator Control System

### Clarifying Questions to Ask
- "How many elevators and how many floors?"
- "Do we support both external hall calls (floor button) and internal cabin calls (destination button)?"
- "What scheduling algorithm should we use?" → LOOK / SCAN is the standard answer.
- "How are multiple elevators coordinated?" → Central Dispatcher routes requests.
- "Should elevators have weight limits or door sensors?" → Mention this shows depth.

### System Requirements
- **Functional**:
  - Elevators move up/down, stop at requested floors, open/close doors.
  - External requests specify floor + direction (UP/DOWN).
  - Internal requests specify only the destination floor.
  - A Dispatcher routes external calls to the most compatible elevator.
- **Non-Functional**:
  - **Concurrency**: Multiple people pressing buttons concurrently.
  - **Real-time**: Requests are processed without perceptible latency.

### Core Class Design
- **Enums**: `Direction` (UP, DOWN, IDLE)
- **Entities**: `Elevator` (implements `Runnable`), `Request`, `Dispatcher`

### Design Patterns to Highlight
- **LOOK Scheduling Algorithm**: Elevator services all requests in one direction, reverses only when no more requests exist in that direction. More efficient than SCAN (no end-of-floor reversal).
- **Observer / Dispatcher Pattern**: Decouple request routing from physical elevator movement.
- **Strategy**: Different dispatcher scoring strategies (nearest car, least loaded, zone-based).

### Complexity Analysis
| Operation | Time Complexity | Notes |
|---|---|---|
| Add Request | O(1) | Boolean array index write |
| Elevator Scan Step | O(maxFloor) | Linear scan of request arrays |
| Dispatcher Routing | O(E) | E = number of elevators |

### Concurrency Discussion
- **Bottleneck**: The main dispatcher thread adds requests while the elevator background thread reads and modifies the same boolean arrays.
- **Solution**: Use a shared `lockObj` as the monitor. Dispatcher notifies (`lockObj.notifyAll()`) the waiting elevator thread when requests arrive. Elevator thread `lockObj.wait()`s when idle.
- **Interview Point**: This is a classic **Producer-Consumer** pattern. Clarify that `@Synchronized("lockObj")` and manual `synchronized(lockObj)` blocks must use the *same* monitor — a common interview trap with Lombok.

### Follow-Up / Scaling Questions
- *"How do you add emergency priority?"* → Insert priority requests at the front of the request queue, bypassing LOOK order.
- *"How do you handle 50 elevators across 100 floors?"* → Zone-based dispatching. Divide floors into zones, assign elevator banks per zone.
- *"What if an elevator breaks down mid-journey?"* → Health monitoring thread, auto-redistribute active requests via Dispatcher.

### Common Mistakes to Avoid
- Don't use a simple `while(true)` busy loop — use `wait()`/`notifyAll()` to avoid CPU burn.
- Don't mix `synchronized(this)` and Lombok `@Synchronized` on the same object — they use different monitors.
- Don't forget to handle the IDLE→MOVING transition when the first request arrives.

---

## 3. Library Management System

### Clarifying Questions to Ask
- "Can a book have multiple physical copies?" → Yes, `Book` (metadata) vs `BookItem` (physical copy).
- "What is the borrowing limit and loan duration?" → e.g., max 5 books for 10 days.
- "Do we support reservations for checked-out books?"
- "Should we calculate and enforce fines for late returns?"
- "What roles exist?" → Member, Librarian.

### System Requirements
- **Functional**:
  - Search catalog by Title, Author, or Subject in O(1) time.
  - Members borrow, return, and reserve book copies.
  - Librarians add or remove book copies.
  - Auto-calculate fine on late return ($1.50/day).
- **Non-Functional**:
  - **Concurrency**: Prevent two members borrowing the same physical copy.
  - Fast catalog lookups via pre-indexed maps.

### Core Class Design
- **Enums**: `BookStatus` (AVAILABLE, LOANED, RESERVED, LOST), `AccountStatus` (ACTIVE, CLOSED, BLACKLISTED), `ReservationStatus` (WAITING, COMPLETED, CANCELLED)
- **Entities**: `Book` (Metadata), `BookItem` (Physical copy), `Account` (Abstract) → `Member`, `Librarian`, `BookLending`, `BookReservation`, `Fine`, `Catalog`, `Library` (Singleton + Facade)

### Design Patterns to Highlight
- **Facade**: `Library` is the single entry point — callers never touch `Catalog`, `BookLending`, or `Fine` directly.
- **Strategy**: Fine calculation strategy based on account type or loan duration.
- **Factory**: Creating accounts (Member vs Librarian) based on role type.

### Complexity Analysis
| Operation | Time Complexity | Notes |
|---|---|---|
| Search by Title/Author | O(1) | Pre-indexed `Map<String, List<Book>>` |
| Borrow Book Item | O(1) | Direct barcode → BookItem map lookup |
| Calculate Fine | O(1) | (returnDate - dueDate) × rate |

### Concurrency Discussion
- **Bottleneck**: Member A and Member B both see `BookItem-BC001` as AVAILABLE and both call `borrowBookItem()` at the same time.
- **Solution**: Synchronize `borrowBookItem` and `reserveBookItem` on the `Library` singleton. The critical section checks status and sets `LOANED` atomically.
- **Interview Point**: Status check and status update must be in the **same synchronized block** — separating them creates a TOCTOU (Time-Of-Check-To-Time-Of-Use) vulnerability.

### Follow-Up / Scaling Questions
- *"How would you scale to 1000 branches?"* → Each `Library` instance per branch, with a shared central `Catalog` service (microservice).
- *"How would you handle digital books?"* → Add `DigitalBookItem` extending `BookItem` with a `downloadUrl` and `expiryDate`.
- *"How would you add a recommendation system?"* → Observer pattern — track borrow history, trigger recommendation updates on `borrowBookItem`.

### Common Mistakes to Avoid
- Don't confuse `Book` (the ISBN metadata record) with `BookItem` (one physical copy with a barcode).
- Don't fine a member if they return the book on time — validate `returnDate <= dueDate`.
- Don't let a `BLACKLISTED` member borrow books — always check `AccountStatus` first.

---

## 4. Chess Game

### Clarifying Questions to Ask
- "Do we need to validate moves for all 6 piece types?"
- "Does the game end only on King capture, or do we need full checkmate detection?"
- "Should we keep a move history for undo/replay?" → Yes, shows depth.
- "Is this for a local 2-player game or online?" → Online implies concurrency.
- "Do we need to handle special moves?" → En passant, castling, pawn promotion.

### System Requirements
- **Functional**:
  - Two-player alternating turns.
  - Validate movement rules for each piece type.
  - Detect captures and track killed pieces.
  - Detect game end (King captured / checkmate).
  - Maintain complete move history.
- **Non-Functional**:
  - Consistent board state between turns.
  - Thread-safe for online multiplayer.

### Core Class Design
- **Enums**: `Color` (WHITE, BLACK), `GameStatus` (ACTIVE, WHITE_WIN, BLACK_WIN, STALEMATE, FORFEIT)
- **Entities**: `Piece` (Abstract with `canMove()`) → `King`, `Queen`, `Rook`, `Knight`, `Bishop`, `Pawn`; `Box`, `Board`, `Move`, `Player`, `Game`

### Design Patterns to Highlight
- **Polymorphism / Template Method**: `Piece.canMove()` is abstract — each subclass encapsulates its own movement validation. This is the core of the OOP design.
- **Command / Memento**: Each `Move` object records the actor, start, end, piece moved, and piece killed. This enables undo, replay, and history logging.
- **Factory**: `Board.resetBoard()` uses a factory to create and place all 32 pieces in their starting positions.

### Complexity Analysis
| Operation | Time Complexity | Notes |
|---|---|---|
| Move Validation | O(1) to O(n) | O(1) for Knight; O(n) for Rook/Bishop path check |
| Board State | O(64) | Always 8×8 grid |
| Move History | O(1) append | ArrayList add |

### Concurrency Discussion
- **Bottleneck**: In online chess, both players are connected via WebSocket. A race condition can allow both to "move" simultaneously.
- **Solution**: Synchronize `playerMove()` on the `Game` instance. Also check `currentTurn == player` inside the critical section.
- **Interview Point**: The check `"is it your turn?"` and the state mutation `"update board"` must be atomic. If separated, Player B can sneak a move while Player A's move is mid-execution.

### Follow-Up / Scaling Questions
- *"How would you add full checkmate detection?"* → After every move, simulate all opponent moves; if King cannot escape any, it's checkmate.
- *"How would you add an AI opponent?"* → Strategy pattern for `MoveSelector`: `RandomMoveSelector`, `MinimaxSelector`.
- *"How would you scale to 1M concurrent games?"* → Each game is an independent `Game` object — horizontal scale via game session sharding.

### Common Mistakes to Avoid
- Don't put move validation in `Board` — it belongs in each `Piece` subclass (polymorphism).
- Don't forget to check if the move puts the player's own King in check (illegal move).
- Don't use raw coordinates in business logic — always use the `Box` abstraction.

---

## 5. Vending Machine

### Clarifying Questions to Ask
- "What states does the machine go through?" → Idle → HasMoney → Dispense → Idle/Refund.
- "How do users insert money?" → Coins of defined denominations.
- "What happens if the product is out of stock?" → Refund the deposit.
- "What if inserted money is insufficient?" → Prompt for more, or refund.
- "Can the admin restock without resetting state?"

### System Requirements
- **Functional**:
  - Accept coins, accumulate deposit.
  - Select product, verify inventory and funds.
  - Dispense product, return change.
  - Allow cancellation and full refund at any time.
- **Non-Functional**:
  - Transaction atomicity — no product dispensed without payment, no payment taken without dispensing.
  - Extensibility — new states and coin types without changing core.

### Core Class Design
- **Enums**: `Coin` (NICKEL=0.05, DIME=0.10, QUARTER=0.25, DOLLAR=1.00)
- **Interfaces**: `State` (insertCoin, selectProduct, dispenseProduct, refund)
- **Entities**: `VendingMachine` (Context), `Product`, `Inventory`, `IdleState`, `HasMoneyState`, `DispenseState`

### Design Patterns to Highlight
- **State Pattern**: (**Crucial — name this immediately**) Instead of nested if-else chains, each state is a class implementing `State`. `VendingMachine` delegates all actions to `currentState`. Adding a new state (e.g., `MaintenanceState`) requires zero changes to existing state classes.
- **Context Pattern**: `VendingMachine` is the Context that stores shared data (deposit, selected product) and transitions between states.

### Complexity Analysis
| Operation | Time Complexity | Notes |
|---|---|---|
| Insert Coin | O(1) | Accumulate deposit |
| Select Product | O(1) | HashMap inventory lookup |
| Dispense Product | O(1) | Decrement count, update state |

### Concurrency Discussion
- **Bottleneck**: Admin thread restocking while a customer thread is mid-transaction trying to buy an item.
- **Solution**: Synchronize all `VendingMachine` public methods. The inventory operations are inside the synchronized context class.
- **Interview Point**: Lock at the `VendingMachine` level here — this is a **single-user terminal** machine, coarse locking is appropriate and correct.

### Follow-Up / Scaling Questions
- *"How would you add support for banknotes?"* → Extend `Coin` enum or create a `Currency` interface with `getCoinValue()`.
- *"How would you track sales analytics?"* → Observer pattern — add a `SalesObserver` that is notified on every `dispenseProduct()`.
- *"How would you add remote monitoring?"* → Add a `MachineStatusReporter` that pushes state snapshots to an external service.

### Common Mistakes to Avoid
- Don't use `if-else` chains on state — that's precisely what the State Pattern avoids.
- Don't dispense before deducting stock — atomicity matters.
- Don't forget the refund path if the machine is in `HasMoneyState` and product runs out.

---

## 6. ATM Machine

### Clarifying Questions to Ask
- "What transactions do we support?" → Balance inquiry, cash withdrawal. Mention deposit as extension.
- "How is the cash dispenser inventory structured?" → Count of $100, $50, $20 bills.
- "How is PIN validated?" → Via external `BankService` (simulate a database call).
- "What happens if the ATM runs out of cash mid-session?" → Reject withdrawal, eject card.
- "Do we handle card blocking after failed PIN attempts?"

### System Requirements
- **Functional**:
  - Insert card → validate PIN → check balance or withdraw.
  - Cash dispenser calculates optimal bill mix (greedy: $100 first, then $50, then $20).
  - Account debit is atomic with cash dispensing.
- **Non-Functional**:
  - Atomic debit-and-dispense (no money dispensed without account debit, and vice versa).
  - Multi-ATM: Multiple ATMs can access the same bank account.

### Core Class Design
- **Interfaces**: `ATMState` (insertCard, enterPin, checkBalance, withdraw, ejectCard)
- **Entities**: `ATM` (Context), `Card`, `Account`, `BankService`, `CashDispenser`, `IdleState`, `PinState`, `TransactionState`

### Design Patterns to Highlight
- **State Pattern**: (`IdleState → PinState → TransactionState → IdleState`). Each state only permits valid actions — pressing "Withdraw" in `IdleState` throws an error naturally without any if-else.
- **Facade**: `BankService` decouples the ATM from the core banking mainframe details.

### Complexity Analysis
| Operation | Time Complexity | Notes |
|---|---|---|
| PIN Validation | O(1) | Map lookup in BankService |
| Bill Calculation | O(1) | Greedy: 3 denominations = 3 divisions |
| Balance Check | O(1) | Direct account balance read |

### Concurrency Discussion
- **Bottleneck**: Customer withdraws $500 at ATM-1 while the same customer withdraws $500 at ATM-2 simultaneously. Both see sufficient balance before either debit completes → overdraft.
- **Solution**: Use a **database-level row lock** on the `Account` record during debit operations (`SELECT FOR UPDATE`). Synchronized `Account.debit()` works for single-instance demos but is insufficient for distributed multi-ATM systems.
- **Interview Point**: This is a real-world **distributed concurrency problem**. Mention optimistic locking (compare-and-swap on balance version number) as the production solution.

### Follow-Up / Scaling Questions
- *"How do you handle network failure between debit and dispense?"* → Two-phase commit or saga pattern: debit first, mark as "PENDING_DISPENSE", confirm after dispensing.
- *"How would you add deposit functionality?"* → New `DepositState`, `CashAcceptor` class, and a credit operation on `Account`.
- *"How would you add card blocking?"* → Track consecutive failed PIN attempts in `PinState`; after 3 failures, call `bankService.blockCard()`.

### Common Mistakes to Avoid
- Don't forget the `ejectCard()` call even on errors — the card must always be returned.
- Don't let `CashDispenser` dispense cash if the account debit fails.
- Don't store PIN in plaintext — mention hashing even if not implementing it.

---

## 7. Hotel Booking System

### Clarifying Questions to Ask
- "Can rooms be booked for a date range?" → Yes, check-in and check-out dates.
- "What room types are supported?" → Standard, Deluxe, Suite.
- "Do we model the full lifecycle?" → Search → Book → Check-In → Check-Out → Housekeeping.
- "Can a room have multiple future bookings for different non-overlapping dates?"
- "Do we support cancellations with a refund policy?"

### System Requirements
- **Functional**:
  - Search available rooms by type and date range.
  - Reserve a room for a guest, generate booking confirmation.
  - Check-in, check-out, and trigger housekeeping workflow.
- **Non-Functional**:
  - **Concurrency**: No double-booking the same room for overlapping dates.
  - **Extensibility**: New room types and dynamic pricing strategies.

### Core Class Design
- **Enums**: `RoomStyle` (STANDARD, DELUXE, SUITE), `RoomStatus` (AVAILABLE, BOOKED, OCCUPIED, BEING_SERVICED), `BookingStatus` (ACTIVE, CHECKED_IN, CHECKED_OUT, CANCELLED)
- **Entities**: `Hotel`, `Room`, `RoomBooking`, `Guest`, `HotelManagementSystem` (Singleton)

### Design Patterns to Highlight
- **Facade**: `HotelManagementSystem` is the central controller — single point of interaction.
- **Strategy**: Dynamic pricing strategy (weekday vs weekend vs holiday rates).

### Complexity Analysis
| Operation | Time Complexity | Notes |
|---|---|---|
| Search Available Rooms | O(R × B) | R = rooms, B = bookings per room |
| Overlap Check | O(1) | `start1 < end2 && start2 < end1` |
| Book Room | O(B) | Scan bookings for overlap then append |

### Concurrency Discussion
- **Bottleneck**: Two web server threads process concurrent booking requests for the same room and same dates. Both query availability simultaneously, both see it as free, both create bookings.
- **Solution**: Synchronize the `bookRoom()` method on the `HotelManagementSystem` singleton. Overlap detection and booking creation are inside a single critical section.
- **Interview Point**: The interval overlap formula (`start1 < end2 && start2 < end1`) must be evaluated **inside** the lock — not before it.

### Follow-Up / Scaling Questions
- *"How would you scale to a hotel chain with 500 properties?"* → Each hotel is an independent bounded context. A central `SearchService` aggregates availability across properties.
- *"How would you add dynamic pricing?"* → Strategy pattern: `WeekendPricingStrategy`, `HolidayPricingStrategy` applied at booking time.
- *"How would you add cancellation with a 50% refund?"* → `CancellationPolicy` strategy interface with time-based refund calculation.

### Common Mistakes to Avoid
- Don't check overlap and then book in two separate steps — TOCTOU vulnerability.
- Don't confuse `RoomStatus` (current physical state) with `BookingStatus` (reservation lifecycle).
- Don't forget that one room can have multiple *future* bookings as long as they don't overlap.

---

## 8. LRU Cache

### Clarifying Questions to Ask
- "What is the cache capacity?" → Configurable.
- "What is the required time complexity for `get` and `put`?" → O(1) — critical constraint.
- "Should it be generic (any key/value type)?" → Yes, use Java generics `<K, V>`.
- "Is thread safety required?" → Yes, multiple threads read/write concurrently.
- "What should happen on cache miss?" → Return null.

### System Requirements
- **Functional**:
  - `get(key)` → Returns value and marks as Most Recently Used. O(1).
  - `put(key, value)` → Inserts. If capacity exceeded, evicts LRU item. O(1).
- **Non-Functional**:
  - Strict O(1) for both operations.
  - Thread-safe under concurrent reads and writes.

### Core Class Design
- **Internal**: `Node<K, V>` (doubly linked list node with `key`, `value`, `prev`, `next`)
- **External**: `LRUCache<K, V>` combining `HashMap<K, Node>` and a doubly linked list with dummy head/tail sentinels.

### Design Patterns to Highlight
- **Doubly Linked List + HashMap Hybrid**: The key insight — the HashMap gives O(1) random access to any node; the doubly linked list gives O(1) insertion and removal anywhere in the list. Together they power both O(1) operations.
- **Sentinel Nodes**: Dummy head and tail nodes eliminate edge case checks for empty list operations.

### Complexity Analysis
| Operation | Time Complexity | Space Complexity |
|---|---|---|
| `get(key)` | O(1) | O(capacity) |
| `put(key, value)` | O(1) | O(capacity) |
| `evict()` | O(1) | — |

### Concurrency Discussion
- **Bottleneck**: Thread A reads a value while Thread B modifies the linked list pointers for the same node.
- **Solution**: Use `ReentrantReadWriteLock`. **Crucially, both `get` AND `put` must acquire the `writeLock()`** — not a read lock for `get` — because `get` modifies list pointers by moving the node to head.
- **Interview Point**: This is a common gotcha. A naive implementation uses `readLock()` for `get`, which allows concurrent reads, but since `get` restructures the doubly linked list (`prev`/`next` pointers), this causes data corruption under concurrency. Both operations are structurally mutating.

### Follow-Up / Scaling Questions
- *"How would you make this distributed?"* → Use Redis, which implements LRU natively. For a custom solution: consistent hashing + distributed LRU per shard.
- *"How would you add TTL (time-to-live) expiry?"* → Add `expiryTime` to `Node`, and during `get`, check if expired before returning.
- *"How would you extend to LFU (Least Frequently Used)?"* → Replace the doubly linked list with a frequency-ordered multi-bucket structure.

### Common Mistakes to Avoid
- Don't use a `LinkedHashMap` in the interview — implement the data structure from scratch to show understanding.
- Don't forget dummy head/tail sentinels — pointer manipulation is error-prone without them.
- Don't use `readLock()` for `get` — explain why both operations need the write lock.

---

## 9. Logger Framework

### Clarifying Questions to Ask
- "What log levels do we support?" → DEBUG, INFO, WARN, ERROR.
- "Can we write to multiple destinations simultaneously?" → Console, File, Database.
- "Should the logger block the calling thread during I/O?" → No, must be async.
- "Do we need log rotation or file size limits?" → Mention as extension.
- "Should logs be timestamped with the source thread name?" → Yes.

### System Requirements
- **Functional**:
  - Format messages with level, timestamp, thread name.
  - Filter by configurable severity threshold.
  - Dispatch to multiple pluggable sinks simultaneously.
- **Non-Functional**:
  - **Non-blocking**: Application threads must not be blocked by log I/O.
  - **Concurrent**: Multiple threads log simultaneously without interference.

### Core Class Design
- **Enums**: `LogLevel` (DEBUG, INFO, WARN, ERROR)
- **Entities**: `LogMessage`, `LogSink` (Interface) → `ConsoleSink`, `FileSink`, `Logger` (Singleton)

### Design Patterns to Highlight
- **Observer (Pub-Sub)**: Appenders (sinks) subscribe to the Logger. When a log is dispatched, all registered sinks are notified.
- **Singleton**: Logger — one global logging engine per application.
- **Producer-Consumer**: Application threads (producers) enqueue log messages; the background daemon thread (consumer) drains and dispatches them.

### Complexity Analysis
| Operation | Time Complexity | Notes |
|---|---|---|
| `log(level, msg)` | O(1) | Non-blocking offer to BlockingQueue |
| Drain & Dispatch | O(S) | S = number of registered sinks |
| Threshold Check | O(1) | Ordinal comparison |

### Concurrency Discussion
- **Bottleneck**: 100 application threads all calling `logger.error()` at once. Synchronous file writes would create a sequential bottleneck and stall all threads.
- **Solution**: Application threads call `logQueue.offer(message)` (non-blocking O(1)). A single dedicated background daemon thread calls `logQueue.poll()` and dispatches to all sinks. This completely decouples application performance from I/O performance.
- **Interview Point**: The daemon thread pattern is the correct answer. Mention `BlockingQueue` is already thread-safe, but the `sinks` list (for adding new appenders) must still be synchronized.

### Follow-Up / Scaling Questions
- *"How would you add a DatabaseSink?"* → Implement `LogSink`. Connection pooling and batch inserts inside the sink handle performance.
- *"How would you add log rotation?"* → `FileSink` tracks file size; when it exceeds threshold, closes current file and opens a new one with timestamp suffix.
- *"What if the queue fills up and the system is overwhelmed?"* → Use `LinkedBlockingQueue` with bounded capacity; drop oldest or block producer. Document the policy.

### Common Mistakes to Avoid
- Don't make `log()` calls synchronous — defeats the purpose of the async design.
- Don't let the worker thread die silently — wrap the drain loop in try-catch and log exceptions to System.err as last resort.
- Don't skip `shutdown()` — the daemon thread must drain remaining logs before the JVM exits.

---

## 10. Splitwise Bill Splitting

### Clarifying Questions to Ask
- "What split types do we support?" → Equal, Exact amounts, Percentage-based.
- "Should we simplify balances to minimize total transactions?" → Yes, Minimum Cash Flow algorithm.
- "Do we support multiple groups or just a flat user list?"
- "Should we validate that percentages sum to 100%?" → Yes.
- "Do we need currency support or is it all single-currency?" → Single currency for LLD scope.

### System Requirements
- **Functional**:
  - Register users. Add expenses specifying payer and split among multiple users.
  - Display individual balance sheet per user.
  - Compute the minimum set of transactions to settle all debts.
- **Non-Functional**:
  - Thread-safe ledger updates.
  - Validation: exact amounts must sum to total; percentages must sum to 100%.

### Core Class Design
- **Enums**: `SplitType` (EQUAL, EXACT, PERCENT)
- **Entities**: `User`, `Split` (Abstract) → `EqualSplit`, `ExactSplit`, `PercentSplit`, `Expense`, `SplitwiseService` (Singleton)

### Design Patterns to Highlight
- **Strategy Pattern**: Split calculation encapsulated per type — adding a new split type (e.g., `SharesSplit`) requires only a new class, no changes to `SplitwiseService`.
- **Min Cash Flow Algorithm**: Use two `PriorityQueue`s — one for biggest debtors (min-heap by balance), one for biggest creditors (max-heap by balance). Greedily match and settle. This is the algorithm interviewers love to discuss.

### Complexity Analysis
| Operation | Time Complexity | Notes |
|---|---|---|
| Add Expense | O(S) | S = number of splits |
| Show Balances | O(U²) | U = users, nested map iteration |
| Simplify Debts | O(U log U) | Priority queue operations |

### Concurrency Discussion
- **Bottleneck**: Two different group members add expenses simultaneously, both modifying the shared `balanceSheet` map.
- **Solution**: Synchronize `addExpense()` on the `SplitwiseService` singleton. Use `ConcurrentHashMap` for the outer balance sheet map for safe concurrent read access between transactions.
- **Interview Point**: The balance update is a multi-step read-modify-write on two entries (debtor's entry and creditor's entry). Both updates must happen atomically inside a single synchronized block.

### Follow-Up / Scaling Questions
- *"How would you add group-level expense splitting?"* → Add a `Group` entity containing a list of `User` members. `SplitwiseService` operates on groups.
- *"How would you add recurring expenses?"* → `ScheduledExpenseService` that fires `addExpense()` on a cron schedule.
- *"How would you scale to millions of users?"* → Shard balance sheets by user ID. Simplification runs as a batch job per group, not globally.

### Common Mistakes to Avoid
- Don't forget to validate split amounts before applying (sum of exact ≠ total → reject).
- Don't include the payer in their own debt calculation for equal splits.
- Don't forget the net balance offset: if A already owes B $10, and B now owes A $6, the net is A owes B only $4.

---

## 11. Ride Booking System (mini Uber)

### Clarifying Questions to Ask
- "How do we represent locations?" → 2D coordinates (x, y) for LLD; mention GPS for production.
- "How do we match drivers to riders?" → Closest available driver by Euclidean distance.
- "How is pricing calculated?" → Base fare + per-unit distance rate.
- "Can a driver reject a ride?" → Mention as extension.
- "Do we need ride cancellation support?"

### System Requirements
- **Functional**:
  - Drivers go ONLINE/OFFLINE. Riders request rides with source + destination.
  - System matches nearest available driver, calculates fare.
  - Trip lifecycle: REQUESTED → IN_PROGRESS → COMPLETED.
- **Non-Functional**:
  - **Concurrency**: Multiple riders cannot claim the same available driver.
  - **Extensibility**: Pluggable pricing strategies (surge, flat rate).

### Core Class Design
- **Enums**: `DriverStatus` (AVAILABLE, BUSY, OFFLINE), `RideStatus` (REQUESTED, IN_PROGRESS, COMPLETED, CANCELLED)
- **Entities**: `Location`, `Rider`, `Driver`, `Ride`, `PricingStrategy` (Interface) → `DistancePricingStrategy`, `RideBookingService` (Singleton)

### Design Patterns to Highlight
- **Strategy**: `PricingStrategy` — pluggable fare calculation (`SurgePricingStrategy`, `SubscriptionPricingStrategy`).
- **Observer**: Notify drivers of new ride requests (push model instead of pull).
- **Facade**: `RideBookingService` as the unified entry point for all operations.

### Complexity Analysis
| Operation | Time Complexity | Notes |
|---|---|---|
| Driver Matching | O(D) | D = active drivers, linear scan |
| Fare Calculation | O(1) | Euclidean distance formula |
| Start/Complete Ride | O(1) | HashMap lookup by rideId |

### Concurrency Discussion
- **Bottleneck**: Rider A and Rider B both request rides. The matching algorithm scans for available drivers. Both see Driver-X as AVAILABLE. Both assign Driver-X → double-booking.
- **Solution**: Synchronize the entire `requestRide()` method — the driver scan AND the `setStatus(BUSY)` call must be atomic.
- **Interview Point**: Finer-grained locking (per-driver lock) is possible but complex. For LLD interviews, synchronizing `requestRide()` is the correct and complete answer.

### Follow-Up / Scaling Questions
- *"How would you scale to millions of drivers in a city?"* → Replace linear O(D) scan with geospatial indexing (QuadTree or Redis Geo). Query nearest N drivers within a radius.
- *"How would you add surge pricing?"* → Observer on `RideBookingService` that monitors demand/supply ratio and swaps `PricingStrategy` dynamically.
- *"How would you add driver rating?"* → Add `rating` field to `Driver`, update after `completeRide()`.

### Common Mistakes to Avoid
- Don't forget to mark the driver as BUSY *before* releasing the lock — otherwise another thread can claim the same driver.
- Don't forget to release the driver (set AVAILABLE) when a ride completes.
- Don't hard-code the distance formula — extract it to `Location.distanceTo()` for clean separation.

---

## 12. Movie Ticket Booking (mini BookMyShow)

### Clarifying Questions to Ask
- "Does a cinema have multiple halls/screens?" → Yes.
- "How are seats structured?" → Seat IDs with tier type (GOLD, SILVER, PLATINUM) and price.
- "Can users book multiple seats in one transaction?" → Yes.
- "How do we handle partial booking failure?" → All-or-nothing: if any seat is taken, the entire transaction fails.
- "Do we need payment processing or just seat reservation?"

### System Requirements
- **Functional**:
  - Search shows by city and movie title.
  - Select multiple seats, verify availability, calculate total price, confirm booking.
- **Non-Functional**:
  - **Strict Concurrency**: Zero double-booking — two users cannot book the same seat.
  - All-or-nothing seat reservation (atomic multi-seat transactions).

### Core Class Design
- **Enums**: `SeatType` (SILVER, GOLD, PLATINUM)
- **Entities**: `Movie`, `ShowSeat`, `Show`, `Cinema`, `Booking`, `BookMyShowService` (Singleton)

### Design Patterns to Highlight
- **Facade**: `BookMyShowService` hides the complexity of cinema navigation, seat lookup, and booking creation behind a clean API.
- **Atomic Check-and-Act**: All seats verified and reserved in a single synchronized block — a classic **all-or-nothing transaction**.

### Complexity Analysis
| Operation | Time Complexity | Notes |
|---|---|---|
| Search Shows | O(C × S) | C = cinemas, S = shows per cinema |
| Seat Availability Check | O(N) | N = seats requested |
| Booking Transaction | O(N) | N = seats reserved |

### Concurrency Discussion
- **Bottleneck**: User A and User B both select Seat-A1 for the 7PM show of a blockbuster. Both check availability, both see AVAILABLE, both try to book → double-sold seat.
- **Solution**: Inside `bookTickets()`, lock on the specific `Show` object (`synchronized(show)`). This serializes all booking attempts for that particular show, while users booking different shows are not blocked.
- **Interview Point**: Show-level locking is the right granularity. Locking the entire `BookMyShowService` would block users of *all* shows — unnecessarily coarse.

### Follow-Up / Scaling Questions
- *"How would you handle peak traffic for a blockbuster release?"* → Distributed seat lock per seat ID using Redis `SET NX PX` (set if not exists with expiry). No single JVM lock.
- *"How would you add a payment gateway?"* → Reserve seats (soft lock for 10 minutes) → process payment → confirm booking or release.
- *"How would you add seat recommendations?"* → Collaborative filtering based on previous booking patterns.

### Common Mistakes to Avoid
- Don't verify seats one by one in separate synchronized blocks — race conditions between each verification.
- Don't forget the all-or-nothing rule: if Seat-3 is taken, undo Seat-1 and Seat-2 reservations.
- Don't lock the entire `BookMyShowService` — lock per `Show` object for correct granularity.

---

## 13. Online Shopping Cart

### Clarifying Questions to Ask
- "Do we check product stock at add-to-cart time, or only at checkout?" → At checkout is standard.
- "Do we support discount coupons?" → Yes, flat and percentage discounts.
- "Is the cart per-session or persistent?" → Per-session for LLD scope.
- "What happens if checkout fails due to insufficient stock?" → Inform user, restore cart.
- "Do we support product variants (size, color)?" → Mention as extension.

### System Requirements
- **Functional**:
  - Add, remove, and update quantities of cart items.
  - Apply coupon codes and calculate final total.
  - Checkout: verify inventory → decrement stock → create order.
- **Non-Functional**:
  - **Concurrency**: Prevent overselling when stock is limited.
  - **Extensibility**: New coupon types and payment processors without core changes.

### Core Class Design
- **Entities**: `Product`, `CartItem`, `ShoppingCart`, `Order`, `CouponStrategy` (Interface) → `FlatDiscountCoupon`, `PercentDiscountCoupon`, `OrderService` (Singleton)

### Design Patterns to Highlight
- **Strategy**: `CouponStrategy` — pluggable discount calculation. Adding `BuyOneGetOneCoupon` requires only a new class.
- **Singleton**: `OrderService` — centralized checkout with synchronized stock management.

### Complexity Analysis
| Operation | Time Complexity | Notes |
|---|---|---|
| Add/Remove/Update Cart | O(1) | HashMap by product ID |
| Calculate Total | O(N) | N = cart items |
| Checkout (place order) | O(N) | N = items, stock check per item |

### Concurrency Discussion
- **Bottleneck**: The last MacBook Pro in stock. Charlie and David both have it in their carts, both hit checkout simultaneously. Both pass the stock check → both orders succeed → stock goes to -1.
- **Solution**: Synchronize `placeOrder()` in `OrderService`. Inside: iterate items, call `product.decrementStock(qty)` atomically. If any decrement fails, rollback all previous decrements and fail the order.
- **Interview Point**: `decrementStock()` itself should be synchronized as well — it's a check-and-decrement combined atomically.

### Follow-Up / Scaling Questions
- *"How would you scale to flash sales with 10,000 concurrent buyers?"* → Redis atomic `DECR` command per product key. Backend verifies order only if `DECR` returns >= 0.
- *"How would you add a wishlist?"* → `Wishlist` entity per `User`, with `moveToCart()` operation.
- *"How would you add order tracking?"* → Add `OrderStatus` enum with an `OrderTrackingService` that updates status at each fulfillment step.

### Common Mistakes to Avoid
- Don't decrement stock at add-to-cart time — only at confirmed checkout.
- Don't forget rollback if any item fails stock check mid-order.
- Don't apply discounts before verifying stock — wasted computation if order fails.

---

## 14. Snake & Ladder Game

### Clarifying Questions to Ask
- "Is the board size standard (100 cells) or configurable?"
- "Can snakes and ladders chain? (Landing on a ladder leads to a snake, etc.)" → Yes, handle recursively.
- "How many players are supported?" → Configurable queue.
- "How many dice?" → One standard die, but make it configurable.
- "Is this an online multiplayer game or local?" → Online implies concurrency.

### System Requirements
- **Functional**:
  - Roll dice, move player token.
  - Apply snake/ladder logic recursively until stable position.
  - Enforce exact landing on cell 100 to win (overshoot = no move).
  - Track leaderboard (order of winners).
- **Non-Functional**:
  - Turn sequence consistency.
  - Extensible board configuration.

### Core Class Design
- **Entities**: `Dice`, `Snake`, `Ladder`, `Board`, `Player`, `Game`

### Design Patterns to Highlight
- **Strategy Pattern**: `Dice` strategy — `StandardDice`, `MultiDice`, `WeightedDice` for testing.
- **Template Method** / **Recursive Resolution**: `Board.getNextPosition()` recursively resolves snake-then-ladder or ladder-then-snake chains.

### Complexity Analysis
| Operation | Time Complexity | Notes |
|---|---|---|
| Roll Dice | O(1) | Random number generation |
| Board Resolution | O(C) | C = chain length (usually O(1) in practice) |
| Check Winner | O(1) | Compare position to board size |
| Play Turn | O(C) | Dominated by board resolution |

### Concurrency Discussion
- **Bottleneck**: Online multiplayer — two players try to roll dice simultaneously, corrupting turn order.
- **Solution**: Synchronize `playTurn()` on the `Game` instance. The `players` queue is modified inside this critical section.
- **Interview Point**: The `Queue.poll()` and `Queue.offer()` must both be inside the synchronized block — otherwise, a player can be polled twice before being re-queued.

### Follow-Up / Scaling Questions
- *"How would you add a tournament mode?"* → Multiple `Game` instances run concurrently; a `TournamentService` tracks results across games.
- *"How would you add power-up cells?"* → `Cell` abstraction with `execute(Player player)` method. `SnakeCell`, `LadderCell`, `PowerUpCell` all implement it (Composite + Visitor pattern).
- *"How would you make the board configurable from a JSON file?"* → Add a `BoardFactory` that parses JSON into `Snake` and `Ladder` objects.

### Common Mistakes to Avoid
- Don't forget the "exact landing" rule — if player is on 98 and rolls 4, they stay on 98.
- Don't skip the recursive chaining — landing on a ladder could lead to a snake head.
- Don't use `synchronized` on the `Queue` separately — the poll/play/offer cycle must be atomic together.

---

## 15. File System

### Clarifying Questions to Ask
- "Do we support both files and directories?" → Yes.
- "Are paths absolute from root?" → Yes, starting with `/`.
- "How is directory size calculated?" → Recursive sum of all contained file sizes.
- "Do we support path-based file creation?" → Yes, auto-create intermediate directories.
- "Do we need move, copy, or delete operations?" → Mention as extension.

### System Requirements
- **Functional**:
  - `mkdir(path)` — Create directories recursively.
  - `ls(path)` — List child entries (sorted).
  - `addContentToFile(path, content)` — Append to file (create if missing, auto-create parent dirs).
  - `readContentFromFile(path)` — Read file content.
  - `getSize(path)` — Recursive size of file or directory.
- **Non-Functional**:
  - Thread-safe concurrent reads and writes.
  - Composite structure supports any depth of nesting.

### Core Class Design
- **Entities**: `Entry` (Abstract base) → `File` (Leaf), `Directory` (Composite containing `List<Entry>`), `FileSystem` (Singleton + Facade)

### Design Patterns to Highlight
- **Composite Pattern**: (**Crucial — name this immediately**) `File` and `Directory` both extend `Entry`. `Directory` holds a `List<Entry>` of children. Clients call `getSize()` on any `Entry` without knowing if it's a file or directory — the tree calculates recursively.
- **Facade**: `FileSystem` exposes a clean `mkdir`, `ls`, `read`, `write` API, hiding the tree traversal and path parsing logic.

### Complexity Analysis
| Operation | Time Complexity | Notes |
|---|---|---|
| `mkdir(path)` | O(D) | D = depth of path |
| `ls(path)` | O(D + N log N) | D = path depth, N = children to sort |
| `addContentToFile` | O(D) | Path resolution depth |
| `readContentFromFile` | O(D) | Path resolution depth |
| `getSize(path)` | O(F) | F = total files in subtree |

### Concurrency Discussion
- **Bottleneck 1**: Two threads simultaneously create `/a/b/c` and `/a/b/d`. Both resolve `/a/b`, both see no child `c` or `d`, both try to add entries to `/a/b` directory simultaneously — corrupting its `children` list.
- **Solution**: Synchronize `FileSystem.resolvePath()` and `Directory.addEntry()`. Thread-safe `children` list mutations prevent `ConcurrentModificationException`.
- **Bottleneck 2**: Thread A writes content to `/a/b/file.txt` while Thread B reads it — dirty read.
- **Solution**: All `File` read/write methods are synchronized. `FileSystem` operations that modify the tree are synchronized at the `FileSystem` level.

### Follow-Up / Scaling Questions
- *"How would you add `delete(path)`?"* → `Directory.removeEntry()` is already synchronized. Add `FileSystem.delete()` that resolves the parent directory and calls `removeEntry()`.
- *"How would you add symbolic links?"* → New `SymbolicLink extends Entry` that stores a target path. `getSize()` resolves and delegates to the target.
- *"How would you make this persistent?"* → Serialize the `Entry` tree to disk (JSON or binary). Load on startup. Write-ahead log for crash recovery.

### Common Mistakes to Avoid
- Don't use `instanceof` checks to differentiate files from directories — use `isDirectory()` from the `Entry` interface (Liskov Substitution).
- Don't forget that `getSize()` on a `Directory` must sum all children recursively, not just direct children.
- Don't forget path edge cases: `/` resolves to root, empty string resolves to root, consecutive slashes should be handled.

---

*End of Handbook — Good luck with your interview! 🚀*
