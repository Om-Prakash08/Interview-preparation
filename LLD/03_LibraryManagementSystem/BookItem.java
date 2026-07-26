package library;

import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;

public class BookItem {
    public enum BookStatus {
        AVAILABLE,
        LOANED,
        RESERVED,
        LOST
    }

    @Getter
    private final String barcode;
    @Getter
    private final Book book;
    private BookStatus status;
    @Getter
    @Setter
    private String rackNumber;

    public BookItem(String barcode, Book book, String rackNumber) {
        this.barcode = barcode;
        this.book = book;
        this.status = BookStatus.AVAILABLE;
        this.rackNumber = rackNumber;
    }

    @Synchronized
    public BookStatus getStatus() {
        return status;
    }

    @Synchronized
    public void setStatus(BookStatus status) {
        this.status = status;
    }
}
