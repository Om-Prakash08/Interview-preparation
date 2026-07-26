package library;

import lombok.Getter;
import lombok.Synchronized;
import java.time.LocalDateTime;

@Getter
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

    @Synchronized
    public void returnBook() {
        this.returnDate = LocalDateTime.now();
    }
}
