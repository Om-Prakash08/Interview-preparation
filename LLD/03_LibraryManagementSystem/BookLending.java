package library;

import java.time.LocalDateTime;

public class BookLending {
    private final String barcode;
    private final String memberId;
    private final LocalDateTime creationDate;
    private final LocalDateTime dueDate;
    private LocalDateTime returnDate;

    public BookLending(String barcode, String memberId, int loanDurationDays) {
        this.barcode = barcode;
        this.memberId = memberId;
        this.creationDate = LocalDateTime.now();
        this.dueDate = creationDate.plusDays(loanDurationDays);
        this.returnDate = null;
    }

    public String getBarcode()             { return barcode; }
    public String getMemberId()            { return memberId; }
    public LocalDateTime getCreationDate() { return creationDate; }
    public LocalDateTime getDueDate()      { return dueDate; }
    public LocalDateTime getReturnDate()   { return returnDate; }

    public synchronized void returnBook() { this.returnDate = LocalDateTime.now(); }
}
