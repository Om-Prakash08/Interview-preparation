package library;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Book {
    private final String isbn;
    private final String title;
    private final String author;
    private final String subject;
}
