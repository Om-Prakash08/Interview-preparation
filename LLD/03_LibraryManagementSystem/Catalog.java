package library;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Catalog implements Search {
    private final Map<String, List<Book>> bookTitles;
    private final Map<String, List<Book>> bookAuthors;
    private final Map<String, List<Book>> bookSubjects;

    public Catalog() {
        this.bookTitles = new HashMap<>();
        this.bookAuthors = new HashMap<>();
        this.bookSubjects = new HashMap<>();
    }

    public synchronized void addBook(Book book) {
        bookTitles.computeIfAbsent(book.getTitle().toLowerCase(), k -> new ArrayList<>()).add(book);
        bookAuthors.computeIfAbsent(book.getAuthor().toLowerCase(), k -> new ArrayList<>()).add(book);
        bookSubjects.computeIfAbsent(book.getSubject().toLowerCase(), k -> new ArrayList<>()).add(book);
    }

    @Override
    public synchronized List<Book> searchByTitle(String title) {
        return new ArrayList<>(bookTitles.getOrDefault(title.toLowerCase(), new ArrayList<>()));
    }

    @Override
    public synchronized List<Book> searchByAuthor(String author) {
        return new ArrayList<>(bookAuthors.getOrDefault(author.toLowerCase(), new ArrayList<>()));
    }

    @Override
    public synchronized List<Book> searchBySubject(String subject) {
        return new ArrayList<>(bookSubjects.getOrDefault(subject.toLowerCase(), new ArrayList<>()));
    }
}
