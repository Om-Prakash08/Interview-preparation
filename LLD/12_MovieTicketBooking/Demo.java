package booking;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FAANG System Design Demo: Movie Ticket Booking (BookMyShow) ===");

        BookMyShowService service = BookMyShowService.getInstance();

        // 1. Initialize data
        Movie movie = new Movie("m-1", "Inception", "Sci-Fi", 148);
        Show show = new Show("show-101", movie, "Screen 1", LocalDateTime.now().plusHours(3), 10);
        
        Cinema cinema = new Cinema("PVR Bangalore", "Bangalore");
        cinema.addShow(show);
        service.addCinema(cinema);

        System.out.println("Registered Movie: Inception. Added show 'show-101' at PVR Bangalore (10 seats).");

        // 2. Search Shows
        System.out.println("\n--- Searching shows for 'Inception' in 'Bangalore' ---");
        List<Show> searchResults = service.searchShows("Bangalore", "Inception");
        for (Show s : searchResults) {
            System.out.printf("Found show %s for movie '%s' at %s (Screen: %s)%n", 
                    s.getShowId(), s.getMovie().getTitle(), cinema.getName(), s.getScreenName());
        }

        // 3. Scenario 1: Bob books Seat-1 and Seat-2
        System.out.println("\n--- Bob books Seat-1 and Seat-2 ---");
        service.bookTickets("show-101", Arrays.asList("Seat-1", "Seat-2"), "Bob");

        // 4. Scenario 2: Concurrency test - Bob and Charlie try to book Seat-3 simultaneously
        System.out.println("\n--- Concurrency check: Bob and Charlie try to book Seat-3 simultaneously ---");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        
        executor.submit(() -> service.bookTickets("show-101", Arrays.asList("Seat-3"), "Bob_Thread"));
        executor.submit(() -> service.bookTickets("show-101", Arrays.asList("Seat-3"), "Charlie_Thread"));

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 5. Scenario 3: Attempting to book already reserved seats
        System.out.println("\n--- Attempting to book already reserved Seat-1 ---");
        service.bookTickets("show-101", Arrays.asList("Seat-1"), "David");

        System.out.println("\n=== Movie Booking Demo Finished successfully ===");
    }
}
