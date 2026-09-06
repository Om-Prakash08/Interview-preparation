package library;

import java.time.LocalDateTime;

public class BookReservation {
    public enum ReservationStatus { WAITING, COMPLETED, CANCELLED }

    private final String barcode;
    private final String memberId;
    private final LocalDateTime creationDate;
    private ReservationStatus status;

    public BookReservation(String barcode, String memberId) {
        this.barcode = barcode;
        this.memberId = memberId;
        this.creationDate = LocalDateTime.now();
        this.status = ReservationStatus.WAITING;
    }

    public String getBarcode()             { return barcode; }
    public String getMemberId()            { return memberId; }
    public LocalDateTime getCreationDate() { return creationDate; }

    public synchronized ReservationStatus getStatus()               { return status; }
    public synchronized void setStatus(ReservationStatus status)    { this.status = status; }
}
