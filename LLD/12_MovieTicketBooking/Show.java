package booking;

import lombok.Getter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
public class Show {
    private final String showId;
    private final Movie movie;
    private final String screenName;
    private final LocalDateTime startTime;
    private final Map<String, ShowSeat> seats;

    public Show(String showId, Movie movie, String screenName, LocalDateTime startTime, int numSeats) {
        this.showId = showId;
        this.movie = movie;
        this.screenName = screenName;
        this.startTime = startTime;
        this.seats = new HashMap<>();
        initializeSeats(numSeats);
    }

    private void initializeSeats(int numSeats) {
        for (int i = 1; i <= numSeats; i++) {
            String seatId = "Seat-" + i;
            SeatType type;
            double price;
            if (i <= numSeats / 5) {
                type = SeatType.PLATINUM;
                price = 300.0;
            } else if (i <= (numSeats * 3) / 5) {
                type = SeatType.GOLD;
                price = 200.0;
            } else {
                type = SeatType.SILVER;
                price = 150.0;
            }
            seats.put(seatId, new ShowSeat(seatId, type, price));
        }
    }
}
