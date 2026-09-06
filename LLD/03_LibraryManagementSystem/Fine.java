package library;

import java.time.Duration;
import java.time.LocalDateTime;

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

    public String getMemberId() { return memberId; }
    public String getBarcode()  { return barcode; }
    public double getAmount()   { return amount; }
    public boolean isPaid()     { return isPaid; }

    public static double calculateFine(LocalDateTime dueDate, LocalDateTime returnDate) {
        if (returnDate.isBefore(dueDate)) return 0.0;
        long daysLate = Duration.between(dueDate, returnDate).toDays();
        return daysLate * 1.50;
    }

    public synchronized void payFine() { this.isPaid = true; }
}
