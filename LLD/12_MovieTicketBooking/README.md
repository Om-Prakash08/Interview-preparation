# 12. Movie Ticket Booking (Mini BookMyShow) — LLD Interview Guide

> **Framework:** Requirements → Entities → Class Design → Implementation → Extensibility

---

## ① Requirements (Clarify First!)

> *"Before I write any code, let me confirm the scope."*

**Functional Requirements:**
- Users can search shows by **city** and **movie title**
- Each show has a set of **seats** with types: `SILVER`, `GOLD`, `PLATINUM` (different prices)
- Users select specific seats and **book tickets** atomically
- System prevents **double-booking** the same seat in the same show
- A `Booking` record confirms the transaction with amount paid

**Non-Functional Requirements:**
- **Thread-safe**: Multiple users concurrently booking seats for the same show
- **Singleton** service — centralized seat allocation
- Lock granularity: **per-show** (not global) to allow parallel bookings across different shows

**Out of Scope:**
- Online payment / refunds
- Seat selection UI (row-column display)
- Cancellation policy

---

## ② Entities (Nouns → Classes)

> *"I'll identify the key nouns to define my classes."*

| Entity | Type | Responsibility |
|---|---|---|
| `SeatType` | Enum | `SILVER, GOLD, PLATINUM` |
| `Movie` | Class | id, title, genre, durationMinutes |
| `ShowSeat` | Class | seatId, SeatType, price, isBooked (atomic reserve/cancel) |
| `Show` | Class | showId, Movie, screenName, startTime, Map of seats |
| `Cinema` | Class | name, city, list of Shows |
| `Booking` | Class | bookingId, Show, list of seats, amountPaid, userName |
| `BookMyShowService` | Singleton | addCinema, searchShows, bookTickets |

---

## ③ Class Design (Design Patterns)

> *"I'll highlight the design patterns used and why."*

### 🔷 Fine-Grained Locking — Per-Show Synchronization
```java
// Inside BookMyShowService.bookTickets():
synchronized (show) {  // ← Lock on SHOW object, not the whole service
    // 1. Check all requested seats are free
    // 2. If any seat is taken → throw exception (fail atomically)
    // 3. Reserve all seats
    // 4. Create Booking record
}
```
**Why?** Locking on `show` means users booking "Inception at 7PM" don't block users booking "Dune at 9PM". This is a key concurrency design decision interviewers love.

### 🔷 Atomic Check-and-Reserve — ShowSeat
```java
// ShowSeat.reserve() — must be atomic:
@Synchronized
public boolean reserve() {
    if (isBooked) return false; // Already taken
    isBooked = true;            // Mark atomically
    return true;
}
```
**Why?** If this check and set were two separate steps, two threads could both see `isBooked=false` and both successfully "book" the same seat.

### 🔷 Class Skeleton
```java
public enum SeatType { SILVER, GOLD, PLATINUM }

public class ShowSeat {
    private final String seatId;
    private final SeatType type;
    private final double price;
    private boolean isBooked;

    @Synchronized public boolean reserve();  // Atomic check-and-set
    @Synchronized public void cancel();
}

public class Show {
    private final String showId;
    private final Movie movie;
    private final String screenName;
    private final LocalDateTime startTime;
    private final Map<String, ShowSeat> seats; // seatId → ShowSeat
}

public class Booking {
    private final String bookingId;
    private final Show show;
    private final List<ShowSeat> seatsBooked;
    private final double amountPaid;
    private final String userName;
}

public class BookMyShowService {
    @Synchronized public static BookMyShowService getInstance();
    public void addCinema(Cinema cinema);
    public List<Show> searchShows(String city, String movieTitle);
    public Booking bookTickets(String showId, List<String> seatIds, String userName);
    // ↑ Locks on show object, not the whole service
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Setup
```java
BookMyShowService service = BookMyShowService.getInstance();

Movie movie = new Movie("m1", "Inception", "Sci-Fi", 148);
Show show = new Show("show-101", movie, "Screen 1",
                     LocalDateTime.now().plusHours(3), 10);
Cinema cinema = new Cinema("PVR", "Bangalore");
cinema.addShow(show);
service.addCinema(cinema);
```

### Search → Book Flow
```java
// 1. Search
List<Show> results = service.searchShows("Bangalore", "Inception");
// → Filters all cinemas in "Bangalore" with shows matching "Inception"

// 2. Book Tickets (critical section per-show)
List<String> seats = Arrays.asList("Seat-1", "Seat-2");
Booking booking = service.bookTickets("show-101", seats, "Bob");
// Internal steps (synchronized on show):
// a. Fetch show from showMap
// b. For each seatId: show.seats.get(seatId) → check !isBooked
// c. If any isBooked → throw "Seat already booked" exception (fail-fast)
// d. reserve() all seats atomically
// e. amountPaid = sum of seat prices
// f. Create Booking, store, return
```

### Concurrent Scenario
```
Thread A: bookTickets("show-101", ["Seat-1", "Seat-2"], "Alice")
Thread B: bookTickets("show-101", ["Seat-1", "Seat-3"], "Bob")

Both enter bookTickets()
Both try to enter synchronized(show) block
→ Only ONE proceeds
→ If Alice wins: Seat-1 reserved → Bob's attempt fails on Seat-1 check
→ Bob gets exception: "Seat-1 already booked"
```

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| Cancellation + refund | Add `cancelBooking(bookingId)` → `seat.cancel()` + refund logic |
| Seat layout (row/col) | Add `row`, `col` fields to `ShowSeat` |
| Booking expiry (hold for 10 min) | Add `heldUntil` timestamp to `ShowSeat`, background cleanup job |
| Food pre-order | Add `List<FoodItem>` to `Booking` |
| Multi-city chain | Already supported — `Cinema` has city field, search filters by city |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `bookTickets()` | `synchronized(show)` (per-show lock) | Prevent seat double-booking without global lock contention |
| `ShowSeat.reserve()` | `@Synchronized` | Atomic check-and-set for `isBooked` |
| `ShowSeat.cancel()` | `@Synchronized` | Safe cancellation without stale reads |
| `BookMyShowService.getInstance()` | `@Synchronized` | Thread-safe singleton creation |
