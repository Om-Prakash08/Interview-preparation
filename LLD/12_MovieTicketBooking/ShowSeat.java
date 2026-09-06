package booking;

public class ShowSeat {
    private final String seatId;
    private final SeatType type;
    private final double price;
    private boolean isBooked;

    public ShowSeat(String seatId, SeatType type, double price) {
        this.seatId = seatId;
        this.type = type;
        this.price = price;
        this.isBooked = false;
    }

    public String getSeatId()  { return seatId; }
    public SeatType getType()  { return type; }
    public double getPrice()   { return price; }

    public synchronized boolean isBooked()  { return isBooked; }
    public synchronized boolean reserve()   { if (isBooked) return false; isBooked = true; return true; }
    public synchronized void cancel()       { isBooked = false; }
}
