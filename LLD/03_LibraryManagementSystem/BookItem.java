package library;

public class BookItem {
    public enum BookStatus { AVAILABLE, LOANED, RESERVED, LOST }

    private final String barcode;
    private final Book book;
    private BookStatus status;
    private String rackNumber;

    public BookItem(String barcode, Book book, String rackNumber) {
        this.barcode = barcode;
        this.book = book;
        this.status = BookStatus.AVAILABLE;
        this.rackNumber = rackNumber;
    }

    public String getBarcode()    { return barcode; }
    public Book getBook()         { return book; }
    public String getRackNumber() { return rackNumber; }
    public void setRackNumber(String rackNumber) { this.rackNumber = rackNumber; }

    public synchronized BookStatus getStatus()            { return status; }
    public synchronized void setStatus(BookStatus status) { this.status = status; }
}
