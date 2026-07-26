package library;

import lombok.Getter;
import lombok.Synchronized;
import java.time.Duration;
import java.time.LocalDateTime;

@Getter
public class Fine {
    private final String memberId;
    private final String barcode;
    private final double amount;
    private boolean isPaid;

    public Fine(String memberId, String barcode, double amount) {
        this.memberId = memberId;
        this.barcode = barcode;
        this.amount = amount;
        this.isPaid = false;
    }

    public static double calculateFine(LocalDateTime dueDate, LocalDateTime returnDate) {
        if (returnDate.isBefore(dueDate)) {
            return 0.0;
        }
        long daysLate = Duration.between(dueDate, returnDate).toDays();
        return daysLate * 1.50;
    }

    @Synchronized
    public void payFine() {
        this.isPaid = true;
    }
}
