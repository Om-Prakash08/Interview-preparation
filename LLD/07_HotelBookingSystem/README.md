# 07. Hotel Booking System — LLD Interview Guide

> **Framework:** Requirements → Entities → Class Design → Implementation → Extensibility

---

## ① Requirements (Clarify First!)

> *"Before I write any code, let me confirm the scope."*

**Functional Requirements:**
- Multiple **Hotels**, each with multiple **Rooms** of different styles
- Room styles: `STANDARD`, `DELUXE`, `SUITE`
- Guests can search available rooms by style and date range
- Guests can **book** a room, **check in**, and **check out**
- After checkout, room goes to `BEING_SERVICED` until housekeeping is complete
- System prevents **double-booking** (overlapping date ranges)

**Non-Functional Requirements:**
- **Thread-safe**: Concurrent booking requests for the same room
- **Singleton** management system — single point of coordination
- Price calculation: `(endDate - startDate) × pricePerNight`

**Out of Scope:**
- Online payment / billing
- Room service requests
- Staff scheduling

---

## ② Entities (Nouns → Classes)

> *"I'll identify the key nouns to define my classes."*

| Entity | Type | Responsibility |
|---|---|---|
| `RoomStyle` | Enum | `STANDARD, DELUXE, SUITE` |
| `RoomStatus` | Enum | `AVAILABLE, BOOKED, OCCUPIED, BEING_SERVICED` |
| `BookingStatus` | Enum | `ACTIVE, CHECKED_IN, CHECKED_OUT, CANCELLED` |
| `Guest` | Class | name, email, phone |
| `Room` | Class | roomNumber, style, pricePerNight, status, bookings list |
| `RoomBooking` | Class | bookingId, room, guest, dates, totalAmount, status |
| `Hotel` | Class | name, location, list of Rooms |
| `HotelManagementSystem` | Singleton | Search, book, checkIn, checkOut, housekeeping |

---

## ③ Class Design (Design Patterns)

> *"I'll highlight the design patterns used and why."*

### 🔷 Facade + Singleton — HotelManagementSystem
```
HotelManagementSystem (Singleton)
    ├── List<Hotel>               hotels
    ├── Map<String, RoomBooking>  activeBookings
    └── bookRoom(), checkIn(), checkOut(), housekeeping()
```
**Why?** Clients interact only with `HotelManagementSystem`. It handles all business logic — searching rooms, managing booking lifecycle, and orchestrating room status transitions.

### 🔷 Date Overlap Check (Key Algorithm)
```java
// RoomBooking.overlaps() — used inside Room.isAvailable()
boolean overlaps(LocalDate start, LocalDate end) {
    return this.startDate.isBefore(end) && start.isBefore(this.endDate);
}
// (start1 < end2) AND (start2 < end1) → overlap exists
```
**Why this matters:** Interviewers love this. A common bug is using `<=` instead of `<`.

### 🔷 Class Skeleton
```java
public class RoomBooking {
    private final String bookingId;
    private final String roomNumber;
    private final Guest guest;
    private final LocalDate startDate, endDate;
    private final double totalAmount;
    private BookingStatus status;

    public boolean overlaps(LocalDate start, LocalDate end);
    @Synchronized public void setStatus(BookingStatus status);
}

public class Room {
    private final String roomNumber;
    private final RoomStyle style;
    private final double pricePerNight;
    private RoomStatus status;
    private final List<RoomBooking> bookings;

    @Synchronized public boolean isAvailable(LocalDate start, LocalDate end);
    @Synchronized public void addBooking(RoomBooking booking);
    @Synchronized public void setStatus(RoomStatus status);
}

public class HotelManagementSystem {
    @Synchronized public static HotelManagementSystem getInstance();
    public List<Room> searchAvailableRooms(Hotel hotel, RoomStyle style, LocalDate start, LocalDate end);
    @Synchronized public RoomBooking bookRoom(Hotel hotel, String roomNumber, Guest guest, LocalDate start, LocalDate end);
    @Synchronized public void checkIn(String bookingId);   // ACTIVE → CHECKED_IN; Room → OCCUPIED
    @Synchronized public void checkOut(String bookingId);  // CHECKED_IN → CHECKED_OUT; Room → BEING_SERVICED
    @Synchronized public void completeHousekeeping(Hotel hotel, String roomNumber); // Room → AVAILABLE
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Setup
```java
HotelManagementSystem hms = HotelManagementSystem.getInstance();

Hotel hotel = new Hotel("Grand Palace", "Bangalore");
Room room = new Room("102", RoomStyle.DELUXE, 150.0);
hotel.addRoom(room);
hms.addHotel(hotel);
```

### Full Lifecycle Flow
```java
// 1. Search
List<Room> available = hms.searchAvailableRooms(
    hotel, RoomStyle.DELUXE, LocalDate.now(), LocalDate.now().plusDays(3)
);

// 2. Book Room
Guest bob = new Guest("Bob", "bob@email.com", "1234");
RoomBooking booking = hms.bookRoom(hotel, "102", bob,
    LocalDate.now(), LocalDate.now().plusDays(3));
// totalAmount = 3 × 150.0 = $450

// 3. Check In (Guest arrives)
hms.checkIn(booking.getBookingId());
// booking.status → CHECKED_IN, room.status → OCCUPIED

// 4. Check Out
hms.checkOut(booking.getBookingId());
// booking.status → CHECKED_OUT, room.status → BEING_SERVICED

// 5. Housekeeping Done
hms.completeHousekeeping(hotel, "102");
// room.status → AVAILABLE (ready for next booking)
```

### Room Status Lifecycle
```
AVAILABLE → BOOKED → OCCUPIED → BEING_SERVICED → AVAILABLE
```

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| Online payment | Add `PaymentService`, call from `bookRoom()` |
| Cancellation & refund | Add `cancelBooking(String bookingId)` + refund policy |
| Room amenities | Add `List<Amenity>` to `Room` entity |
| Multi-hotel chain | Already supported — `addHotel()` accepts any `Hotel` |
| Loyalty points | Add `LoyaltyAccount` linked to `Guest` |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `HotelManagementSystem.bookRoom` | `@Synchronized` | Prevent double-booking same room for overlapping dates |
| `Room.isAvailable` | `@Synchronized` | Atomic overlap check against booking list |
| `Room.addBooking` | `@Synchronized` | Safe concurrent list modification |
| `RoomBooking.setStatus` | `@Synchronized` | Consistent status across threads |
