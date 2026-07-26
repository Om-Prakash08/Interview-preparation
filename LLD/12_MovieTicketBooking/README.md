# 12. Movie Ticket Booking (Java LLD Solution)

This folder contains a complete, thread-safe Java implementation of a Movie Ticket Booking System (mini BookMyShow).

Below is the **Complete Class Skeleton and API Design** so you can understand the entire system architecture, fields, and method signatures without looking at the source code.

---

## 1. Class Diagram / Architecture Skeleton

### Data Models & Constants
```java
public enum SeatType { SILVER, GOLD, PLATINUM }

@Getter
@AllArgsConstructor
public class Movie {
    private final String id;
    private final String title;
    private final String genre;
    private final int durationMinutes;
}

public class ShowSeat {
    @Getter private final String seatId;
    @Getter private final SeatType type;
    @Getter private final double price;
    private boolean isBooked;

    @Synchronized public boolean isBooked();
    @Synchronized public boolean reserve(); // Checks & marks booked atomically
    @Synchronized public void cancel();
}

@Getter
public class Show {
    private final String showId;
    private final Movie movie;
    private final String screenName;
    private final LocalDateTime startTime;
    private final Map<String, ShowSeat> seats;

    public Show(String showId, Movie movie, String screen, LocalDateTime start, int numSeats);
}
```

### Cinemas & Booking Records
```java
@Getter
public class Cinema {
    private final String name;
    private final String city;
    private final List<Show> shows;

    public void addShow(Show show);
}

@Getter
public class Booking {
    private final String bookingId;
    private final Show show;
    private final List<ShowSeat> seatsBooked;
    private final double amountPaid;
    private final String userName;
}
```

### Booking Central Service (Facade / Singleton)
```java
public class BookMyShowService {
    @Synchronized public static BookMyShowService getInstance();
    public void addCinema(Cinema cinema);
    public List<Show> searchShows(String city, String movieTitle);

    public Booking bookTickets(String showId, List<String> seatIds, String userName); // Thread-safe seat transaction
}
```

---

## 2. Core Workflow & Usage

Here is how show searches and transaction seat allocations are processed:

```java
BookMyShowService service = BookMyShowService.getInstance();

// 1. Setup Show
Movie movie = new Movie("m1", "Inception", "Sci-Fi", 148);
Show show = new Show("show-101", movie, "Screen 1", LocalDateTime.now().plusHours(3), 10);
Cinema cinema = new Cinema("PVR", "Bangalore");
cinema.addShow(show);
service.addCinema(cinema);

// 2. Search Shows
List<Show> results = service.searchShows("Bangalore", "Inception");

// 3. Book Tickets (Atomic check-and-reserve transaction)
List<String> seatsToBook = Arrays.asList("Seat-1", "Seat-2");
Booking booking = service.bookTickets("show-101", seatsToBook, "Bob");
```

---

## 3. Concurrency & Thread-Safety Details
- **Lock Contention Management**: Inside `BookMyShowService.bookTickets`, we acquire a lock on the specific `Show` object (`synchronized (show)`) rather than synchronizing the entire service method. This keeps the transaction block scoped, meaning users booking tickets for "Show A" do not block users checkout out tickets for "Show B".
- **Atomic Check-and-Act**: All selected seats are checked for status first, and only reserved if all of them are available. If even one seat is taken, the transaction fails immediately, preventing partial double-booking.
