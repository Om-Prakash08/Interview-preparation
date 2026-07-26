package hotel;

import lombok.Getter;
import lombok.Synchronized;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Room {
    @Getter
    private final String roomNumber;
    @Getter
    private final RoomStyle style;
    @Getter
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

    @Synchronized
    public RoomStatus getStatus() {
        return status;
    }

    @Synchronized
    public void setStatus(RoomStatus status) {
        this.status = status;
    }

    @Synchronized
    public List<RoomBooking> getBookings() {
        return new ArrayList<>(bookings);
    }

    @Synchronized
    public boolean isAvailable(LocalDate start, LocalDate end) {
        if (status == RoomStatus.BEING_SERVICED) {
            return false;
        }
        for (RoomBooking booking : bookings) {
            if (booking.getStatus() == BookingStatus.ACTIVE || booking.getStatus() == BookingStatus.CHECKED_IN) {
                if (booking.overlaps(start, end)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Synchronized
    public void addBooking(RoomBooking booking) {
        bookings.add(booking);
    }
}
