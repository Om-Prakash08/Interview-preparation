# LLD FAANG Interview Preparation Handbook

This handbook is designed to help you ace your Low-Level Design (LLD) interviews at FAANG and other top tech companies. For each of the 15 classic questions, this guide outlines:
1. **Clarifying Questions** to ask the interviewer to scope the problem.
2. **System Requirements** (Functional & Non-Functional) to establish before writing code.
3. **Core Entities & Class Design** (Class Structure).
4. **Design Patterns** to highlight to impress the interviewer.
5. **Concurrency & Thread Safety** bottlenecks to proactively discuss.

---

## Index of Questions
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
- "Does the parking lot have multiple levels and entrance/exit gates?" (Yes, usually)
- "What types of vehicles do we support?" (Motorcycle, Car, Truck, Van)
- "Are there different spot types matching vehicle sizes?" (Yes: Motorcycle, Compact, Large)
- "How is the pricing calculated?" (Hourly rates based on vehicle type)
- "Should we handle ticketing and payments at the exit?" (Yes)

### System Requirements
- **Functional**:
  - Support multi-level parking.
  - Automatically allocate the nearest available spot of compatible size when a vehicle enters.
  - Generate a ticket at the entrance with entry time.
  - Calculate fee, accept payment, and free the spot at exit.
- **Non-Functional**:
  - **Concurrency**: Entry gates must handle simultaneous arrivals without double-booking the same spot.

### Core Class Design
- **Enums**: `VehicleType` (MOTORCYCLE, CAR, TRUCK), `ParkingSpotType` (MOTORCYCLE, COMPACT, LARGE)
- **Entities**: `ParkingLot` (Singleton), `Level`, `ParkingSpot`, `Vehicle` (Abstract) and subclasses, `Ticket`
- **Strategies**: `FeeCalculator`, `PaymentStrategy` (Cash, Card, Wallet)

### Design Patterns to Highlight
- **Singleton**: The `ParkingLot` instance.
- **Strategy**: `FeeCalculator` and `PaymentStrategy` to allow swapping pricing/payment rules.

### Concurrency Discussion (FAANG Level)
- **Bottleneck**: Two entry gates trying to park cars simultaneously at the same level.
- **Solution**: Synchronize the `parkVehicle(Vehicle)` method inside the `Level` class. Use Java locks (`ReentrantLock`) on levels to ensure atomic check-and-book operations.

---

## 2. Elevator Control System

### Clarifying Questions to Ask
- "How many elevators are in the building and how many floors?" (e.g. 2 elevator cabs, 10 floors)
- "Do we support external (hall panel) and internal (cabin panel) calls?" (Yes)
- "What scheduling algorithm should we use?" (LOOK / SCAN algorithm is standard)
- "How are multiple elevators coordinated?" (A central Dispatcher routes requests)

### System Requirements
- **Functional**:
  - Elevators move up/down, stop at floors, open/close doors.
  - External requests have a source floor and direction (UP/DOWN).
  - Internal requests have a target floor.
- **Non-Functional**:
  - **Concurrency**: Process concurrent button presses inside and outside cabins in real time.

### Core Class Design
- **Enums**: `Direction` (UP, DOWN, IDLE)
- **Entities**: `Elevator` (implements `Runnable`), `Request`, `Dispatcher`, `Floor`

### Design Patterns to Highlight
- **State Pattern**: Manage elevator cabin states (IDLE, MOVING, DOOR_OPEN).
- **Dispatcher/Controller Pattern**: Decouple the request routing logic from the physical elevator movement.

### Concurrency Discussion (FAANG Level)
- **Bottleneck**: Main thread receiving user inputs while elevator threads are moving and modifying their floor queues.
- **Solution**: Maintain requests in synchronized collections (e.g. boolean arrays for quick O(1) checks). Synchronize state reads and writes using a shared lock inside `Elevator`.

---

## 3. Library Management System

### Clarifying Questions to Ask
- "Can a book have multiple physical copies?" (Yes, `Book` vs `BookItem`)
- "What is the borrowing limit and loan duration?" (e.g., max 5 books for 10 days)
- "Do we support book reservations?" (Yes, if a copy is loaned, members can reserve it)
- "Should we calculate fines for late returns?" (Yes)

### System Requirements
- **Functional**:
  - Search catalog by Title, Author, or Subject.
  - Members can borrow, return, and reserve book copies.
  - Librarians can add or remove book copies.
- **Non-Functional**:
  - Fast catalog searches.

### Core Class Design
- **Enums**: `BookStatus` (AVAILABLE, LOANED, RESERVED), `AccountStatus` (ACTIVE, BLACKLISTED)
- **Entities**: `Book` (Metadata), `BookItem` (Physical copy), `Account` (Base) -> `Member`, `Librarian`, `BookLending`, `BookReservation`, `Fine`, `Catalog`, `Library` (Singleton)

### Design Patterns to Highlight
- **Facade**: The `Library` class acts as a single point of interaction for all operations.
- **Strategy**: Fine calculation strategies based on user type or late duration.

### Concurrency Discussion (FAANG Level)
- **Bottleneck**: Multiple members trying to borrow or reserve the same physical `BookItem` concurrently.
- **Solution**: Synchronize `borrowBookItem` and `reserveBookItem` in `Library` to ensure atomic check-and-borrow execution.

---

## 4. Chess Game

### Clarifying Questions to Ask
- "Do we need to validate moves for all chess pieces?" (Yes, demonstrate polymorphism with a few key pieces like Knight, Pawn, and Rook)
- "How does the game end?" (Capturing the King or checkmate)
- "Should we keep a history of moves?" (Yes, to support undo/redo or game logging)

### System Requirements
- **Functional**:
  - Two-player game with alternating turns.
  - Check-and-apply movement rules for pieces.
  - Detect capturing and track active/killed pieces.
- **Non-Functional**:
  - Maintain consistent board states.

### Core Class Design
- **Enums**: `Color` (WHITE, BLACK), `GameStatus` (ACTIVE, WHITE_WIN, BLACK_WIN)
- **Entities**: `Piece` (Abstract) and concrete pieces (`King`, `Queen`, `Rook`, `Knight`, `Bishop`, `Pawn`), `Box` (Coordinate), `Board` (8x8 grid), `Move`, `Player`, `Game`

### Design Patterns to Highlight
- **Command / Memento Pattern**: Representing a move as an object (`Move`) makes it trivial to implement an undo/redo feature.
- **Factory Pattern**: Initializing chess pieces on board reset.

### Concurrency Discussion (FAANG Level)
- **Bottleneck**: Turn sequence synchronization in online multi-player chess.
- **Solution**: Synchronize the `playerMove` method on the `Game` instance.

---

## 5. Vending Machine

### Clarifying Questions to Ask
- "What states does the vending machine go through?" (Idle, HasMoney, Dispensing, Refunding)
- "How do users insert money?" (Support coins/bills of different values)
- "What happens if the item is out of stock or change cannot be returned?" (Refund deposit)

### System Requirements
- **Functional**:
  - Accept coins/bills, track total deposit.
  - Allow product selection, verify inventory and funds.
  - Dispense product and return change.
  - Allow transaction cancellation and refund.
- **Non-Functional**:
  - Ensure transaction atomicity.

### Core Class Design
- **Enums**: `Coin` (NICKEL, DIME, QUARTER, DOLLAR)
- **Interfaces**: `State`
- **Entities**: `VendingMachine` (Context), `Product`, `Inventory`, `IdleState`, `HasMoneyState`, `DispenseState`

### Design Patterns to Highlight
- **State Pattern**: **Crucial.** Encapsulate state-specific behaviors inside separate state classes rather than using nested if-else checks.

### Concurrency Discussion (FAANG Level)
- **Bottleneck**: User checking inventory/inserting coins while admin refuels stock.
- **Solution**: Synchronize inventory modification methods. Lock transitions in the context class `VendingMachine` during the transaction flow.

---

## 6. ATM Machine

### Clarifying Questions to Ask
- "What transactions do we support?" (Balance inquiry, cash withdrawal)
- "How is the cash dispenser inventory structured?" (Track number of $100, $50, $20 bills)
- "How do we handle pin validation?" (Connect to external `BankService`)

### System Requirements
- **Functional**:
  - Validate card, authenticate PIN.
  - Dispense cash (calculate optimal bill breakdown), debit user account.
  - Display balance.
- **Non-Functional**:
  - Atomicity of debit and cash dispensing.

### Core Class Design
- **Enums**: `TransactionType`
- **Interfaces**: `ATMState`
- **Entities**: `ATM` (Context), `Card`, `Account`, `BankService`, `CashDispenser`, `IdleState`, `PinState`, `TransactionState`

### Design Patterns to Highlight
- **State Pattern**: Managing session cycles (Idle -> CardInserted -> Authenticated -> Dispensing).
- **Facade**: Decouple connection to core bank mainframe database.

### Concurrency Discussion (FAANG Level)
- **Bottleneck**: Dispensing cash while another thread attempts to debit/withdraw from the same account.
- **Solution**: Use database locks on the `Account` entry. Synchronize cash drawer updates (`CashDispenser`) to prevent double-spending in multi-ATM scenarios.

---

## 7. Hotel Booking System

### Clarifying Questions to Ask
- "Can rooms be booked for a date range?" (Yes, start and end dates)
- "What types of rooms are supported?" (Standard, Deluxe, Suite)
- "How do we handle check-in, check-out, and housekeeping?" (Yes, room status changes)

### System Requirements
- **Functional**:
  - Search available rooms of a specific style for a given date range.
  - Reserve a room, issue invoice.
  - Check-in, check-out, and housekeeping workflows.
- **Non-Functional**:
  - **Concurrency**: Prevent double-booking rooms for overlapping dates.

### Core Class Design
- **Enums**: `RoomStyle` (STANDARD, DELUXE, SUITE), `RoomStatus` (AVAILABLE, BOOKED, OCCUPIED, SERVICE), `BookingStatus` (ACTIVE, CANCELLED, CHECKED_OUT)
- **Entities**: `Hotel`, `Room`, `RoomBooking`, `Guest`, `HotelManagementSystem` (Singleton)

### Design Patterns to Highlight
- **Strategy Pattern**: Dynamic room pricing depending on weekends, holidays, or seasons.
- **Facade**: Centralized bookings controller.

### Concurrency Discussion (FAANG Level)
- **Bottleneck**: Multiple users trying to book the exact same room for overlapping dates simultaneously.
- **Solution**: Implement synchronization at the `bookRoom` method in the centralized management system. Calculate date interval overlaps (`start1 < end2 && start2 < end1`) atomically.

---

## 8. LRU Cache

### Clarifying Questions to Ask
- "What is the capacity of the cache?" (Configurable integer)
- "What should be the time complexity of read/write operations?" (O(1) time complexity)
- "Does it need to be thread-safe?" (Yes, concurrent reads and writes)

### System Requirements
- **Functional**:
  - `get(key)`: Retrieve value, mark key as recently used.
  - `put(key, value)`: Insert key-value. Evict the Least Recently Used item if capacity is exceeded.
- **Non-Functional**:
  - Strict O(1) performance and thread safety.

### Core Class Design
- **Entities**: `Node` (Doubly Linked List node), `LRUCache` (combining `HashMap` and custom list)

### Design Patterns to Highlight
- **Doubly Linked List + Hash Map Combination**: Explain how this hybrid structure enables O(1) removals and random lookups.

### Concurrency Discussion (FAANG Level)
- **Bottleneck**: Concurrent reads and writes. A standard `get` updates node pointers, so a simple read lock is not enough.
- **Solution**: Use a `ReentrantReadWriteLock`. Both `get` and `put` must acquire the `writeLock()` because both operations restructure the doubly linked list pointers.

---

## 9. Logger Framework

### Clarifying Questions to Ask
- "What log levels do we support?" (INFO, DEBUG, ERROR)
- "Can we log to multiple destinations?" (Console, File, Database)
- "Should the logger block the application?" (No, asynchronous logging to prevent CPU blocking)

### System Requirements
- **Functional**:
  - Format messages with levels, timestamps, and thread details.
  - Filter logs based on severity thresholds.
- **Non-Functional**:
  - Highly concurrent, asynchronous log processing.

### Core Class Design
- **Enums**: `LogLevel` (DEBUG, INFO, ERROR)
- **Entities**: `LogMessage`, `LogSink` (Interface) -> `ConsoleSink`, `FileSink`, `Logger` (Singleton)

### Design Patterns to Highlight
- **Observer (Pub-Sub)**: Sinks subscribe to the logger to receive log dispatch packets.
- **Singleton**: The `Logger` context.

### Concurrency Discussion (FAANG Level)
- **Bottleneck**: Blocking the application thread during file system or database write operations.
- **Solution**: Implement asynchronous logging. The application thread pushes logs to a thread-safe `BlockingQueue` (non-blocking). A background daemon thread drains the queue and writes to the destinations.

---

## 10. Splitwise Bill Splitting

### Clarifying Questions to Ask
- "What split algorithms do we support?" (Equal, Exact amounts, Percentages)
- "Should we simplify net balances to minimize transactions?" (Yes, using a debt simplification algorithm)
- "Do we support user groups?" (Yes)

### System Requirements
- **Functional**:
  - Register users. Add expenses paid by one user split among multiple users.
  - Display individual ledger balances.
  - Calculate minimal transactions to settle all debts.
- **Non-Functional**:
  - Thread-safe balance sheet updates.

### Core Class Design
- **Enums**: `SplitType` (EQUAL, EXACT, PERCENT)
- **Entities**: `User`, `Split` (Abstract) -> `EqualSplit`, `ExactSplit`, `PercentSplit`, `Expense`, `SplitwiseService` (Singleton)

### Design Patterns to Highlight
- **Strategy Pattern**: Encapsulate different splitting validation and calculation rules.
- **Min Cash Flow algorithm (Greedy/Heap)**: Explain how priority queues are used to match the largest debtors and creditors to settle debts.

### Concurrency Discussion (FAANG Level)
- **Bottleneck**: Multiple users adding expenses in the same group concurrently, updating shared balances.
- **Solution**: Use `ConcurrentHashMap` for balance sheet mappings and synchronize the `addExpense` method on the service instance.

---

## 11. Ride Booking System (mini Uber)

### Clarifying Questions to Ask
- "How do we locate drivers?" (Riders and drivers have coordinates: x, y)
- "How do we match riders and drivers?" (Match closest available driver using Euclidean distance)
- "How is pricing calculated?" (Base rate + per-kilometer charge)

### System Requirements
- **Functional**:
  - Drivers go online/offline. Riders request rides.
  - System matches driver, calculates fare, starts and completes rides.
- **Non-Functional**:
  - Prevent matching the same driver to multiple riders.

### Core Class Design
- **Enums**: `DriverStatus` (AVAILABLE, BUSY), `RideStatus` (REQUESTED, IN_PROGRESS, COMPLETED)
- **Entities**: `Location`, `Rider`, `Driver`, `Ride`, `PricingStrategy` (Interface), `RideBookingService` (Singleton)

### Design Patterns to Highlight
- **Strategy Pattern**: The `PricingStrategy` makes it easy to add surge pricing or flat rate pricing models.
- **Observer Pattern**: Notify drivers when a new ride request is generated.

### Concurrency Discussion (FAANG Level)
- **Bottleneck**: Two riders booking rides at the same time trying to claim the same nearby driver.
- **Solution**: Synchronize the `requestRide` method in the centralized `RideBookingService` to ensure driver lookup and state assignment (`status = BUSY`) are atomic.

---

## 12. Movie Ticket Booking (mini BookMyShow)

### Clarifying Questions to Ask
- "Does a cinema have multiple halls and screens?" (Yes)
- "How are seat layouts structured?" (Rows and columns, with tiers: GOLD, SILVER, PLATINUM)
- "How do we handle concurrent booking of the exact same seats?" (Lock seats during transaction)

### System Requirements
- **Functional**:
  - Search shows by city and movie title.
  - Select seats, check availability, calculate price, and checkout.
- **Non-Functional**:
  - **Strict Concurrency**: Zero double-booking of seats.

### Core Class Design
- **Enums**: `SeatType` (SILVER, GOLD, PLATINUM)
- **Entities**: `Movie`, `ShowSeat`, `Show`, `Cinema`, `Booking`, `BookMyShowService` (Singleton)

### Design Patterns to Highlight
- **Facade**: The booking service coordinates searches, bookings, and transactions through a unified interface.

### Concurrency Discussion (FAANG Level)
- **Bottleneck**: Hundreds of users trying to book the same popular seats for a blockbuster show at the exact same moment.
- **Solution**: Use synchronization on the `Show` object during check-and-book. Alternatively, use a distributed cache key (like Redis locks) for individual seat IDs if scaling horizontally.

---

## 13. Online Shopping Cart

### Clarifying Questions to Ask
- "Do we check product stock during checkout?" (Yes)
- "Do we support discount coupon codes?" (Yes, flat discounts or percentages)
- "What happens if checkout payment fails?" (Restore product stock)

### System Requirements
- **Functional**:
  - Add, remove, and update quantities of items in the cart.
  - Apply coupons, calculate final total.
  - Checkout, verify inventory, decrement stock, and place order.
- **Non-Functional**:
  - Thread-safe stock validation.

### Core Class Design
- **Entities**: `Product`, `CartItem`, `ShoppingCart`, `Order`, `CouponStrategy` (Interface), `OrderService` (Singleton)

### Design Patterns to Highlight
- **Strategy Pattern**: Coupon strategies and Payment processor strategies.
- **Builder Pattern**: Constructing complex `Order` objects.

### Concurrency Discussion (FAANG Level)
- **Bottleneck**: Users checking out items whose inventory is low, causing race conditions on stock levels.
- **Solution**: Synchronize stock verification and decrement operations. Use Java locks (`ReentrantLock`) on the `placeOrder` method to make the transaction atomic.

---

## 14. Snake & Ladder Game

### Clarifying Questions to Ask
- "Is the board size configurable?" (Standard 100 cells)
- "Can snakes and ladders be nested?" (Yes, sliding down a snake can land on a ladder, or vice versa)
- "How many players are supported?" (Configurable queue of players)

### System Requirements
- **Functional**:
  - Roll dice, move tokens.
  - Apply snakes and ladders logic recursively.
  - Enforce exact rolls to reach the final cell (100).
- **Non-Functional**:
  - Turn sequence consistency.

### Core Class Design
- **Entities**: `Dice`, `Snake`, `Ladder`, `Board`, `Player`, `Game`

### Design Patterns to Highlight
- **State Pattern**: Manage game status phases (INIT, ACTIVE, FINISHED).
- **Strategy Pattern**: Swap different board layout generation rules.

### Concurrency Discussion (FAANG Level)
- **Bottleneck**: Turn sequence synchronization in online multi-player games.
- **Solution**: Keep players in a thread-safe `Queue` and synchronize the `playTurn` execution to guarantee consistent state changes.

---

## 15. File System

### Clarifying Questions to Ask
- "Do we support directories and files?" (Yes)
- "How is directory size calculated?" (Recursive sum of all child sizes)
- "Do paths start from root?" (Yes, absolute paths starting with `/`)

### System Requirements
- **Functional**:
  - `mkdir(path)`: Create directories recursively.
  - `ls(path)`: List child entries.
  - `addContentToFile(path, content)`: Append content to file (create if missing).
  - `getSize(path)`: Get size of file or directory.
- **Non-Functional**:
  - Thread-safe updates to the file directory tree.

### Core Class Design
- **Entities**: `Entry` (Abstract base), `File` (Leaf), `Directory` (Composite), `FileSystem` (Singleton)

### Design Patterns to Highlight
- **Composite Pattern**: **Crucial.** Directory contains a list of `Entry` objects, allowing files and directories to be treated uniformly.

### Concurrency Discussion (FAANG Level)
- **Bottleneck**: Concurrent creation of nested directories and simultaneous reads/writes to files.
- **Solution**: Synchronize path resolution (`resolvePath`) and file write operations inside `FileSystem` to prevent race conditions on directory maps and file contents.
