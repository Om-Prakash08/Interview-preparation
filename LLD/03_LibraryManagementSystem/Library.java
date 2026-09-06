package library;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Library {
    private static Library instance;
    private final String name;
    private final Catalog catalog;
    private final Map<String, BookItem> bookItems;
    private final Map<String, Account> accounts;
    private final Map<String, BookLending> activeLendings;
    private final Map<String, Queue<BookReservation>> reservations;
    private final List<Fine> fines;

    private static final int MAX_BOOKS_BORROWED_LIMIT = 5;
    private static final int MAX_LENDING_DAYS = 10;

    private Library(String name) {
        this.name = name;
        this.catalog = new Catalog();
        this.bookItems = new ConcurrentHashMap<>();
        this.accounts = new ConcurrentHashMap<>();
        this.activeLendings = new ConcurrentHashMap<>();
        this.reservations = new ConcurrentHashMap<>();
        this.fines = Collections.synchronizedList(new ArrayList<>());
    }

    public static synchronized Library getInstance() {
        if (instance == null) {
            instance = new Library("FAANG Central Library");
        }
        return instance;
    }

    public Catalog getCatalog() { return catalog; }

    public void registerAccount(Account account) {
        accounts.put(account.getId(), account);
        System.out.printf("[Account] Registered user %s (%s)%n", account.getName(), account.getClass().getSimpleName());
    }

    public void addBookItem(Librarian librarian, BookItem bookItem) {
        if (librarian == null || librarian.getStatus() != Account.AccountStatus.ACTIVE) {
            System.out.println("Only active librarians can add books.");
            return;
        }
        bookItems.put(bookItem.getBarcode(), bookItem);
        catalog.addBook(bookItem.getBook());
        System.out.printf("[Library Admin] Added book item '%s' (ISBN: %s, Barcode: %s)%n", 
                bookItem.getBook().getTitle(), bookItem.getBook().getIsbn(), bookItem.getBarcode());
    }

    public synchronized boolean borrowBookItem(String memberId, String barcode) {
        Account account = accounts.get(memberId);
        if (!(account instanceof Member)) {
            System.out.println("Only members can borrow books.");
            return false;
        }
        Member member = (Member) account;

        if (member.getStatus() != Account.AccountStatus.ACTIVE) {
            System.out.printf("Member %s is not active.%n", member.getName());
            return false;
        }

        if (member.getBorrowedBooksCount() >= MAX_BOOKS_BORROWED_LIMIT) {
            System.out.printf("Member %s has reached the maximum borrowed limit of %d books.%n", member.getName(), MAX_BOOKS_BORROWED_LIMIT);
            return false;
        }

        BookItem bookItem = bookItems.get(barcode);
        if (bookItem == null) {
            System.out.println("Book item not found.");
            return false;
        }

        if (bookItem.getStatus() == BookItem.BookStatus.LOANED || bookItem.getStatus() == BookItem.BookStatus.LOST) {
            System.out.printf("Book item '%s' is not available for borrowing.%n", bookItem.getBook().getTitle());
            return false;
        }

        Queue<BookReservation> bookReservations = reservations.get(barcode);
        if (bookReservations != null && !bookReservations.isEmpty()) {
            BookReservation firstRes = bookReservations.peek();
            if (!firstRes.getMemberId().equals(memberId)) {
                System.out.printf("Book item '%s' is reserved by another member.%n", bookItem.getBook().getTitle());
                return false;
            }
            bookReservations.poll();
            firstRes.setStatus(BookReservation.ReservationStatus.COMPLETED);
        }

        bookItem.setStatus(BookItem.BookStatus.LOANED);
        BookLending lending = new BookLending(barcode, memberId, MAX_LENDING_DAYS);
        activeLendings.put(barcode, lending);
        member.incrementBorrowedBooksCount();
        System.out.printf("[Borrow] Member %s successfully borrowed '%s' (Due in %d days)%n", 
                member.getName(), bookItem.getBook().getTitle(), MAX_LENDING_DAYS);
        return true;
    }

    public synchronized void reserveBookItem(String memberId, String barcode) {
        Account account = accounts.get(memberId);
        if (!(account instanceof Member)) return;
        Member member = (Member) account;

        BookItem bookItem = bookItems.get(barcode);
        if (bookItem == null) return;

        if (bookItem.getStatus() == BookItem.BookStatus.AVAILABLE) {
            System.out.printf("Book item '%s' is available. Borrow it directly.%n", bookItem.getBook().getTitle());
            return;
        }

        reservations.computeIfAbsent(barcode, k -> new LinkedList<>()).add(new BookReservation(barcode, memberId));
        if (bookItem.getStatus() != BookItem.BookStatus.RESERVED) {
            bookItem.setStatus(BookItem.BookStatus.RESERVED);
        }
        System.out.printf("[Reserve] Member %s reserved '%s'%n", member.getName(), bookItem.getBook().getTitle());
    }

    public synchronized void returnBookItem(String memberId, String barcode) {
        Account account = accounts.get(memberId);
        if (!(account instanceof Member)) return;
        Member member = (Member) account;

        BookItem bookItem = bookItems.get(barcode);
        if (bookItem == null) return;

        BookLending lending = activeLendings.remove(barcode);
        if (lending == null || !lending.getMemberId().equals(memberId)) {
            System.out.println("This book was not borrowed by this member.");
            return;
        }

        lending.returnBook();
        member.decrementBorrowedBooksCount();

        double fineAmt = Fine.calculateFine(lending.getDueDate(), lending.getReturnDate());
        if (fineAmt > 0) {
            Fine fine = new Fine(memberId, barcode, fineAmt);
            fines.add(fine);
            System.out.printf("[Fine Issued] Member %s returned '%s' late. Fine: $%.2f%n", 
                    member.getName(), bookItem.getBook().getTitle(), fineAmt);
        } else {
            System.out.printf("[Return] Member %s returned '%s' on time.%n", member.getName(), bookItem.getBook().getTitle());
        }

        Queue<BookReservation> bookReservations = reservations.get(barcode);
        if (bookReservations != null && !bookReservations.isEmpty()) {
            bookItem.setStatus(BookItem.BookStatus.RESERVED);
            System.out.printf("[Notification] Book '%s' is now held for reserving member %s.%n", 
                    bookItem.getBook().getTitle(), bookReservations.peek().getMemberId());
        } else {
            bookItem.setStatus(BookItem.BookStatus.AVAILABLE);
        }
    }
}
