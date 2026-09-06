package hotel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Room {
    private final String roomNumber;
    private final RoomStyle style;
    private final double pricePerNight;
    private RoomStatus status;
    private final List<RoomBooking> bookings;

    public Room(String roomNumber, RoomStyle style, double pricePerNight) {
        this.roomNumber = roomNumber;
        this.style = style;
        this.pricePerNight = pricePerNight;
        this.status = RoomStatus.AVAILABLE;
        this.bookings = new ArrayList<>();
    }

    public String getRoomNumber()    { return roomNumber; }
    public RoomStyle getStyle()      { return style; }
    public double getPricePerNight() { return pricePerNight; }

    public synchronized RoomStatus getStatus()            { return status; }
    public synchronized void setStatus(RoomStatus status) { this.status = status; }
    public synchronized List<RoomBooking> getBookings()   { return new ArrayList<>(bookings); }
    public synchronized void addBooking(RoomBooking b)    { bookings.add(b); }

    public synchronized boolean isAvailable(LocalDate start, LocalDate end) {
        if (status == RoomStatus.BEING_SERVICED) return false;
        for (RoomBooking booking : bookings) {
            if (booking.getStatus() == BookingStatus.ACTIVE || booking.getStatus() == BookingStatus.CHECKED_IN) {
                if (booking.overlaps(start, end)) return false;
            }
        }
        return true;
    }
}
