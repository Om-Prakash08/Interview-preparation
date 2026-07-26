package library;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FAANG System Design Demo: Library Management System ===");

        Library library = Library.getInstance();

        // 1. Setup Accounts
        Librarian librarian = new Librarian("admin-1", "pass123", "Alice (Librarian)");
        Member m1 = new Member("mem-1", "mypass", "Bob");
        Member m2 = new Member("mem-2", "mypass", "Charlie");
        Member m3 = new Member("mem-3", "mypass", "David");

        library.registerAccount(librarian);
        library.registerAccount(m1);
        library.registerAccount(m2);
        library.registerAccount(m3);

        // 2. Add Books
        Book book1 = new Book("978-0132350884", "Clean Code", "Robert C. Martin", "Software Engineering");
        Book book2 = new Book("978-0134685991", "Effective Java", "Joshua Bloch", "Java");

        BookItem item1 = new BookItem("BC-001", book1, "Rack A1");
        BookItem item2 = new BookItem("BC-002", book2, "Rack B3");

        library.addBookItem(librarian, item1);
        library.addBookItem(librarian, item2);

        // 3. Search Catalog
        System.out.println("\n--- Searching Book Catalog ---");
        List<Book> searchResults = library.getCatalog().searchByTitle("clean code");
        for (Book b : searchResults) {
            System.out.printf("Found search result: '%s' by %s%n", b.getTitle(), b.getAuthor());
        }

        // 4. Simulate Concurrent Borrowing
        System.out.println("\n--- Simulating Concurrent Borrowing (Thread Safety Check) ---");
        System.out.println("Bob, Charlie, and David simultaneously try to borrow 'Clean Code' (Barcode: BC-001)");

        ExecutorService executor = Executors.newFixedThreadPool(3);
        executor.submit(() -> library.borrowBookItem("mem-1", "BC-001"));
        executor.submit(() -> library.borrowBookItem("mem-2", "BC-001"));
        executor.submit(() -> library.borrowBookItem("mem-3", "BC-001"));

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 5. Reserve Book
        System.out.println("\n--- Simulating Book Reservation ---");
        // Charlie and David try to reserve since the book is loaned out
        library.reserveBookItem("mem-2", "BC-001");
        library.reserveBookItem("mem-3", "BC-001");

        // 6. Return and Reservation Transfer
        System.out.println("\n--- Simulating Book Return ---");
        // Attempt return from all members to ensure the one who borrowed returns it
        library.returnBookItem("mem-1", "BC-001");
        library.returnBookItem("mem-2", "BC-001");
        library.returnBookItem("mem-3", "BC-001");

        // 7. Charlie borrows the book he reserved
        System.out.println("\n--- Charlie borrows the reserved book ---");
        library.borrowBookItem("mem-2", "BC-001");

        System.out.println("\n=== Demo Finished successfully ===");
    }
}
