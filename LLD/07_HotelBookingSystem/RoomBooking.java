package hotel;

import lombok.Getter;
import lombok.Synchronized;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Getter
public class RoomBooking {
    private final String bookingId;
    private final String roomNumber;
    private final Guest guest;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private BookingStatus status;
    private final double totalAmount;

    public RoomBooking(String bookingId, String roomNumber, Guest guest, LocalDate startDate, LocalDate endDate, double ratePerNight) {
        this.bookingId = bookingId;
        this.roomNumber = roomNumber;
        this.guest = guest;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = BookingStatus.ACTIVE;
        long days = ChronoUnit.DAYS.between(startDate, endDate);
        this.totalAmount = (days <= 0 ? 1 : days) * ratePerNight;
    }

    @Synchronized
    public BookingStatus getStatus() {
        return status;
    }

    @Synchronized
    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public boolean overlaps(LocalDate start, LocalDate end) {
        return this.startDate.isBefore(end) && start.isBefore(this.endDate);
    }
}
