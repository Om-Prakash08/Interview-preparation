# 07. Hotel Booking System (Java LLD Solution)

This folder contains a complete, thread-safe Java implementation of a Hotel Booking System.

Below is the **Complete Class Skeleton and API Design** so you can understand the entire system architecture, fields, and method signatures without looking at the source code.

---

## 1. Class Diagram / Architecture Skeleton

### Enums & Guests
```java
public enum RoomStyle { STANDARD, DELUXE, SUITE }
public enum RoomStatus { AVAILABLE, BOOKED, OCCUPIED, BEING_SERVICED }
public enum BookingStatus { ACTIVE, CHECKED_IN, CHECKED_OUT, CANCELLED }

@Getter
@AllArgsConstructor
public class Guest {
    private final String name;
    private final String email;
    private final String phone;
}
```

### Core Booking Entities
```java
@Getter
public class RoomBooking {
    private final String bookingId;
    private final String roomNumber;
    private final Guest guest;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final double totalAmount;
    private BookingStatus status;

    @Synchronized public BookingStatus getStatus();
    @Synchronized public void setStatus(BookingStatus status);
    public boolean overlaps(LocalDate start, LocalDate end); // (start1 < end2 && start2 < end1)
}

public class Room {
    @Getter private final String roomNumber;
    @Getter private final RoomStyle style;
    @Getter private final double pricePerNight;
    private RoomStatus status;
    private final List<RoomBooking> bookings;

    @Synchronized public RoomStatus getStatus();
    @Synchronized public void setStatus(RoomStatus status);
    @Synchronized public List<RoomBooking> getBookings(); // Returns copy
    @Synchronized public boolean isAvailable(LocalDate start, LocalDate end); // Overlap check
    @Synchronized public void addBooking(RoomBooking booking);
}

@Getter
public class Hotel {
    private final String name;
    private final String location;
    private final List<Room> rooms;

    public void addRoom(Room room);
}
```

### Management Service (Facade / Singleton)
```java
public class HotelManagementSystem {
    @Synchronized public static HotelManagementSystem getInstance();
    public void addHotel(Hotel hotel);
    public List<Room> searchAvailableRooms(Hotel hotel, RoomStyle style, LocalDate start, LocalDate end);

    @Synchronized public RoomBooking bookRoom(Hotel hotel, String roomNumber, Guest guest, LocalDate start, LocalDate end);
    @Synchronized public void checkIn(String bookingId);
    @Synchronized public void checkOut(String bookingId);
    @Synchronized public void completeHousekeeping(Hotel hotel, String roomNumber);
}
```

---

## 2. Core Workflow & Usage

Here is how search, reservation, check-in, and housekeeping are coordinated:

```java
HotelManagementSystem hms = HotelManagementSystem.getInstance();

// 1. Setup hotel and room catalog
Hotel hotel = new Hotel("Grand Palace", "Bangalore");
Room room = new Room("102", RoomStyle.DELUXE, 150.0);
hotel.addRoom(room);
hms.addHotel(hotel);

// 2. Search availability
List<Room> available = hms.searchAvailableRooms(hotel, RoomStyle.DELUXE, LocalDate.now(), LocalDate.now().plusDays(3));

// 3. Book Room
Guest bob = new Guest("Bob", "bob@email.com", "1234");
RoomBooking booking = hms.bookRoom(hotel, "102", bob, LocalDate.now(), LocalDate.now().plusDays(3));

// 4. Lifecycle transitions
hms.checkIn(booking.getBookingId());  // Room status: OCCUPIED
hms.checkOut(booking.getBookingId()); // Room status: BEING_SERVICED
hms.completeHousekeeping(hotel, "102"); // Room status: AVAILABLE
```

---

## 3. Concurrency & Thread-Safety Details
- **Double Booking Prevention**: The `bookRoom` method is synchronized (`@Synchronized`) at the `HotelManagementSystem` level. This locks the date availability scan and reservation creation step, preventing concurrent threads from booking the same room for overlapping dates.
- **Room Status Locking**: State modifications on `Room` and `RoomBooking` objects use `@Synchronized` monitor locks.
