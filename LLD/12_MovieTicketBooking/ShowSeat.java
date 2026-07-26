package booking;

import lombok.Getter;
import lombok.Synchronized;

public class ShowSeat {
    @Getter
    private final String seatId;
    @Getter
    private final SeatType type;
    @Getter
    private final double price;
    private boolean isBooked;

    public ShowSeat(String seatId, SeatType type, double price) {
        this.seatId = seatId;
        this.type = type;
        this.price = price;
        this.isBooked = false;
    }

    @Synchronized
    public boolean isBooked() {
        return isBooked;
    }

    @Synchronized
    public boolean reserve() {
        if (isBooked) {
            return false;
        }
        isBooked = true;
        return true;
    }

    @Synchronized
    public void cancel() {
        isBooked = false;
    }
}
