# 03. Library Management System (Java LLD Solution)

This folder contains a complete, thread-safe Java implementation of a Library Management System.

Below is the **Complete Class Skeleton and API Design** so you can understand the entire system architecture, fields, and method signatures without looking at the source code.

---

## 1. Class Diagram / Architecture Skeleton

### Data Models & Constants
```java
public enum BookStatus { AVAILABLE, LOANED, RESERVED, LOST }
public enum AccountStatus { ACTIVE, CLOSED, BLACKLISTED }
public enum ReservationStatus { WAITING, COMPLETED, CANCELLED }

@Getter
@AllArgsConstructor
public class Book {
    private final String isbn;
    private final String title;
    private final String author;
    private final String subject;
}

public class BookItem {
    @Getter private final String barcode;
    @Getter private final Book book;
    @Getter @Setter private String rackNumber;

    @Synchronized public BookStatus getStatus();
    @Synchronized public void setStatus(BookStatus status);
}

@Getter
public class BookLending {
    private final String barcode;
    private final String memberId;
    private final LocalDateTime creationDate;
    private final LocalDateTime dueDate;
    private LocalDateTime returnDate;

    @Synchronized public void returnBook();
}

@Getter
public class BookReservation {
    private final String barcode;
    private final String memberId;
    private final LocalDateTime creationDate;
    private ReservationStatus status;

    @Synchronized public ReservationStatus getStatus();
    @Synchronized public void setStatus(ReservationStatus status);
}

@Getter
public class Fine {
    private final String memberId;
    private final String barcode;
    private final double amount;
    private boolean isPaid;

    public static double calculateFine(LocalDateTime dueDate, LocalDateTime returnDate); // $1.50 per day late
    @Synchronized public void payFine();
}
```

### Accounts Structure
```java
public abstract class Account {
    @Getter private final String id;
    @Getter private final String password;
    @Getter private final String name;
    private AccountStatus status;

    @Synchronized public AccountStatus getStatus();
    @Synchronized public void setStatus(AccountStatus status);
}

class Member extends Account {
    private int borrowedBooksCount;

    @Synchronized public int getBorrowedBooksCount();
    @Synchronized public void incrementBorrowedBooksCount();
    @Synchronized public void decrementBorrowedBooksCount();
}

class Librarian extends Account { ... }
```

### Library & Search Catalog (Facade / Singleton)
```java
public class Catalog {
    public void addBook(Book book);
    public List<Book> searchByTitle(String title);   // O(1) Map search
    public List<Book> searchByAuthor(String author); // O(1) Map search
}

public class Library {
    @Getter private final Catalog catalog;

    @Synchronized public static Library getInstance();
    public void registerAccount(Account account);
    public void addBookItem(Librarian librarian, BookItem bookItem);

    @Synchronized public boolean borrowBookItem(String memberId, String barcode);
    @Synchronized public void reserveBookItem(String memberId, String barcode);
    @Synchronized public void returnBookItem(String memberId, String barcode); // Returns book and handles late fine creation
}
```

---

## 2. Core Workflow & Usage

Here is how to search, borrow, reserve, and return books using the Facade API:

```java
Library library = Library.getInstance();

// 1. Setup accounts and catalog
Librarian admin = new Librarian("admin-1", "pwd", "Alice");
library.registerAccount(admin);

Member member = new Member("mem-1", "pwd", "Bob");
library.registerAccount(member);

Book book = new Book("978-0132350884", "Clean Code", "Robert C. Martin", "Software");
BookItem item = new BookItem("BC-001", book, "Rack-A");
library.addBookItem(admin, item);

// 2. Search Catalog
List<Book> results = library.getCatalog().searchByTitle("Clean Code");

// 3. Borrow Book
library.borrowBookItem("mem-1", "BC-001");

// 4. Return Book
library.returnBookItem("mem-1", "BC-001");
```

---

## 3. Concurrency & Thread-Safety Details
- **Transaction Safety**: The `borrowBookItem`, `reserveBookItem`, and `returnBookItem` methods are synchronized (`@Synchronized`) at the `Library` singleton level. This locks the active loan maps and reservation queues, preventing race conditions where multiple members checkout the same physical `BookItem` concurrently.
- **Atomic Book Status**: `BookItem` uses `@Synchronized` status checks.
