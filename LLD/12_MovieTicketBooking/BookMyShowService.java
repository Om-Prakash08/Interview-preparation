package booking;

import lombok.Synchronized;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BookMyShowService {
    private static BookMyShowService instance;
    private final List<Cinema> cinemas;
    private final Map<String, Show> shows;
    private final Map<String, Booking> bookings;

    private BookMyShowService() {
        this.cinemas = new ArrayList<>();
        this.shows = new ConcurrentHashMap<>();
        this.bookings = new ConcurrentHashMap<>();
    }

    @Synchronized
    public static BookMyShowService getInstance() {
        if (instance == null) {
            instance = new BookMyShowService();
        }
        return instance;
    }

    public void addCinema(Cinema cinema) {
        cinemas.add(cinema);
        for (Show show : cinema.getShows()) {
            shows.put(show.getShowId(), show);
        }
    }

    public List<Show> searchShows(String city, String movieTitle) {
        List<Show> result = new ArrayList<>();
        for (Cinema cinema : cinemas) {
            if (cinema.getCity().equalsIgnoreCase(city)) {
                for (Show show : cinema.getShows()) {
                    if (show.getMovie().getTitle().equalsIgnoreCase(movieTitle)) {
                        result.add(show);
                    }
                }
            }
        }
        return result;
    }

    // Thread-safe seat booking
    public Booking bookTickets(String showId, List<String> seatIds, String userName) {
        Show show = shows.get(showId);
        if (show == null) {
            System.out.println("[Booking Error] Show not found.");
            return null;
        }

        synchronized (show) {
            Map<String, ShowSeat> layout = show.getSeats();
            List<ShowSeat> seatsToBook = new ArrayList<>();

            // 1. Verify availability
            for (String seatId : seatIds) {
                ShowSeat seat = layout.get(seatId);
                if (seat == null) {
                    System.out.printf("[Booking Failed for %s] Seat %s does not exist!%n", userName, seatId);
                    return null;
                }
                if (seat.isBooked()) {
                    System.out.printf("[Booking Failed for %s] Seat %s is already booked!%n", userName, seatId);
                    return null;
                }
                seatsToBook.add(seat);
            }

            // 2. Reserve seats
            for (ShowSeat seat : seatsToBook) {
                seat.reserve();
            }

            // 3. Create booking
            String bookingId = "BMS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Booking booking = new Booking(bookingId, show, seatsToBook, userName);
            bookings.put(bookingId, booking);

            System.out.printf("[Booking Success] %s confirmed booking %s for %d seats. Paid: $%.2f%n",
                    userName, bookingId, seatsToBook.size(), booking.getAmountPaid());
            
            return booking;
        }
    }
}
