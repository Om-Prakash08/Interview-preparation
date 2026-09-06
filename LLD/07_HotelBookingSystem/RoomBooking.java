package hotel;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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

    public String getBookingId()    { return bookingId; }
    public String getRoomNumber()   { return roomNumber; }
    public Guest getGuest()         { return guest; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate()   { return endDate; }
    public double getTotalAmount()  { return totalAmount; }

    public synchronized BookingStatus getStatus()               { return status; }
    public synchronized void setStatus(BookingStatus status)    { this.status = status; }

    public boolean overlaps(LocalDate start, LocalDate end) {
        return this.startDate.isBefore(end) && start.isBefore(this.endDate);
    }
}
