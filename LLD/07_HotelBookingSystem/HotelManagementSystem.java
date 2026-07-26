package hotel;

import lombok.Synchronized;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class HotelManagementSystem {
    private static HotelManagementSystem instance;
    private final List<Hotel> hotels;
    private final Map<String, RoomBooking> bookings;

    private HotelManagementSystem() {
        this.hotels = new ArrayList<>();
        this.bookings = new ConcurrentHashMap<>();
    }

    @Synchronized
    public static HotelManagementSystem getInstance() {
        if (instance == null) {
            instance = new HotelManagementSystem();
        }
        return instance;
    }

    public void addHotel(Hotel hotel) {
        hotels.add(hotel);
    }

    public List<Room> searchAvailableRooms(Hotel hotel, RoomStyle style, LocalDate start, LocalDate end) {
        List<Room> availableRooms = new ArrayList<>();
        for (Room room : hotel.getRooms()) {
            if (room.getStyle() == style && room.isAvailable(start, end)) {
                availableRooms.add(room);
            }
        }
        return availableRooms;
    }

    // Thread-safe booking process to avoid double booking
    @Synchronized
    public RoomBooking bookRoom(Hotel hotel, String roomNumber, Guest guest, LocalDate start, LocalDate end) {
        Room targetRoom = null;
        for (Room room : hotel.getRooms()) {
            if (room.getRoomNumber().equals(roomNumber)) {
                targetRoom = room;
                break;
            }
        }

        if (targetRoom == null) {
            System.out.printf("[Booking Failed] Room %s not found in hotel %s.%n", roomNumber, hotel.getName());
            return null;
        }

        if (!targetRoom.isAvailable(start, end)) {
            System.out.printf("[Booking Failed] Room %s is not available for requested dates [%s to %s]%n", 
                    roomNumber, start, end);
            return null;
        }

        String bookingId = "BKG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        RoomBooking booking = new RoomBooking(bookingId, roomNumber, guest, start, end, targetRoom.getPricePerNight());
        
        targetRoom.addBooking(booking);
        bookings.put(bookingId, booking);

        // If booking starts today, update room status
        if (start.equals(LocalDate.now())) {
            targetRoom.setStatus(RoomStatus.BOOKED);
        }

        System.out.printf("[Booking Success] Booking %s confirmed for Room %s. Total: $%.2f%n",
                bookingId, roomNumber, booking.getTotalAmount());
        return booking;
    }

    @Synchronized
    public void checkIn(String bookingId) {
        RoomBooking booking = bookings.get(bookingId);
        if (booking == null) return;

        booking.setStatus(BookingStatus.CHECKED_IN);
        // Find room and mark OCCUPIED
        for (Hotel hotel : hotels) {
            for (Room room : hotel.getRooms()) {
                if (room.getRoomNumber().equals(booking.getRoomNumber())) {
                    room.setStatus(RoomStatus.OCCUPIED);
                    System.out.printf("[Check-In] Guest checked into Room %s. Booking: %s.%n", 
                            room.getRoomNumber(), bookingId);
                    return;
                }
            }
        }
    }

    @Synchronized
    public void checkOut(String bookingId) {
        RoomBooking booking = bookings.get(bookingId);
        if (booking == null) return;

        booking.setStatus(BookingStatus.CHECKED_OUT);
        // Find room and mark BEING_SERVICED
        for (Hotel hotel : hotels) {
            for (Room room : hotel.getRooms()) {
                if (room.getRoomNumber().equals(booking.getRoomNumber())) {
                    room.setStatus(RoomStatus.BEING_SERVICED);
                    System.out.printf("[Check-Out] Guest checked out of Room %s. Invoice of $%.2f settled.%n", 
                            room.getRoomNumber(), booking.getTotalAmount());
                    return;
                }
            }
        }
    }

    @Synchronized
    public void completeHousekeeping(Hotel hotel, String roomNumber) {
        for (Room room : hotel.getRooms()) {
            if (room.getRoomNumber().equals(roomNumber) && room.getStatus() == RoomStatus.BEING_SERVICED) {
                room.setStatus(RoomStatus.AVAILABLE);
                System.out.printf("[Housekeeping] Room %s has been serviced and is now AVAILABLE.%n", roomNumber);
                return;
            }
        }
    }
}
