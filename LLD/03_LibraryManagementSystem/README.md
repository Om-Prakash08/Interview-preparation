# 03. Library Management System — LLD Interview Guide

> **Framework:** Requirements → Entities → Class Design → Implementation → Extensibility

---

## ① Requirements (Clarify First!)

> *"Before I write any code, let me confirm the scope."*

**Functional Requirements:**
- Two user roles: **Librarian** (admin) and **Member** (borrower)
- Members can **search** books by title or author
- Members can **borrow**, **reserve**, and **return** book copies (physical items)
- System tracks **due dates** and calculates **late fines** automatically
- Librarian can **add** books and manage accounts

**Non-Functional Requirements:**
- **Thread-safe**: Multiple members may try to borrow the same book concurrently
- **Singleton Library** — single point of coordination
- O(1) catalog search using `HashMap`

**Out of Scope:**
- Online payment for fines
- E-book or digital catalog

---

## ② Entities (Nouns → Classes)

> *"I'll identify the key nouns to define my classes."*

| Entity | Type | Responsibility |
|---|---|---|
| `Book` | Class | Metadata: ISBN, title, author, subject |
| `BookItem` | Class | Physical copy with barcode, rack number, status |
| `BookLending` | Class | Active loan record (barcode, memberId, due date) |
| `BookReservation` | Class | Reservation record with status tracking |
| `Fine` | Class | Late fee record; calculates $1.50/day |
| `Account` | Abstract | Base for `Member` and `Librarian` |
| `Member` | Class | Can borrow books; tracks `borrowedBooksCount` |
| `Librarian` | Class | Can add books and manage system |
| `Catalog` | Class | HashMap-based O(1) search |
| `Library` | Singleton | Facade orchestrating all operations |

**Key Enums:**
```
BookStatus        → AVAILABLE, LOANED, RESERVED, LOST
AccountStatus     → ACTIVE, CLOSED, BLACKLISTED
ReservationStatus → WAITING, COMPLETED, CANCELLED
```

---

## ③ Class Design (Design Patterns)

> *"I'll highlight the design patterns used and why."*

### 🔷 Facade + Singleton — Library
```
Library (Singleton)
    ├── Catalog (search)
    ├── Map<String, Account>      (accounts)
    ├── Map<String, BookItem>     (inventory)
    ├── Map<String, BookLending>  (active loans)
    └── Map<String, List<BookReservation>> (reservations)
```
**Why?** The `Library` class is a Facade — clients don't interact with `Catalog`, `BookItem`, or `BookLending` directly. All business logic is in one place.

### 🔷 Template Method — Account Hierarchy
```
Account (abstract)
    ├── Member  → borrowedBooksCount limit (max 5)
    └── Librarian → no borrow limit
```

### 🔷 Class Skeleton
```java
public class BookItem {
    private final String barcode;
    private final Book book;

    @Synchronized public BookStatus getStatus();
    @Synchronized public void setStatus(BookStatus status);
}

public class Fine {
    public static double calculateFine(LocalDateTime dueDate, LocalDateTime returnDate);
    // $1.50 per day late
}

public class Catalog {
    public List<Book> searchByTitle(String title);   // O(1) HashMap lookup
    public List<Book> searchByAuthor(String author); // O(1) HashMap lookup
}

public class Library {
    @Synchronized public static Library getInstance();
    @Synchronized public boolean borrowBookItem(String memberId, String barcode);
    @Synchronized public void reserveBookItem(String memberId, String barcode);
    @Synchronized public void returnBookItem(String memberId, String barcode);
    // returnBookItem auto-creates Fine if returned after dueDate
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Setup
```java
Library library = Library.getInstance();

Librarian admin = new Librarian("admin-1", "pwd", "Alice");
library.registerAccount(admin);

Member member = new Member("mem-1", "pwd", "Bob");
library.registerAccount(member);

Book book = new Book("978-0132350884", "Clean Code", "Robert C. Martin", "Software");
BookItem item = new BookItem("BC-001", book, "Rack-A");
library.addBookItem(admin, item); // Only Librarians can add
```

### Search → Borrow → Return Flow
```java
// 1. Search
List<Book> results = library.getCatalog().searchByTitle("Clean Code");

// 2. Borrow (inside borrowBookItem):
//    - Validate member is ACTIVE and under borrowing limit
//    - Check BookItem status == AVAILABLE
//    - Create BookLending record (dueDate = now + 14 days)
//    - Set BookItem status = LOANED
library.borrowBookItem("mem-1", "BC-001");

// 3. Return (inside returnBookItem):
//    - Mark BookLending.returnDate = now
//    - If returnDate > dueDate → create Fine record
//    - Set BookItem status = AVAILABLE
//    - If reservation queue non-empty → set status = RESERVED
library.returnBookItem("mem-1", "BC-001");
```

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| Email notification on due date | Add `NotificationService`, call from `borrowBookItem` |
| Fine payment processing | Add `payFine(String memberId)` to `Library` Facade |
| Book ratings / reviews | Add `Review` entity linked to `Book` |
| Multiple library branches | `LibraryNetwork` aggregates multiple `Library` Singletons |
| Max borrow limit per member | Already in `Member.borrowedBooksCount` — just tune the constant |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `Library.borrowBookItem` | `@Synchronized` | Prevent two members claiming same `BookItem` |
| `Library.returnBookItem` | `@Synchronized` | Prevent race during status update + fine check |
| `BookItem.setStatus` | `@Synchronized` | Atomic status flip |
| `BookReservation.setStatus` | `@Synchronized` | Consistent reservation state |
