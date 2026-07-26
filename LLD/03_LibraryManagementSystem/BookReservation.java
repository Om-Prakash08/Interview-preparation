package library;

import lombok.Getter;
import lombok.Synchronized;
import java.time.LocalDateTime;

@Getter
public class BookReservation {
    public enum ReservationStatus {
        WAITING,
        COMPLETED,
        CANCELLED
    }

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

    @Synchronized
    public ReservationStatus getStatus() {
        return status;
    }

    @Synchronized
    public void setStatus(ReservationStatus status) {
        this.status = status;
    }
}
