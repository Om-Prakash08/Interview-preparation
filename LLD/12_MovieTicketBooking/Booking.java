package booking;

import java.util.List;

public class Booking {
    private final String bookingId;
    private final Show show;
    private final List<ShowSeat> seatsBooked;
    private final double amountPaid;
    private final String userName;

    public Booking(String bookingId, Show show, List<ShowSeat> seatsBooked, String userName) {
        this.bookingId = bookingId;
        this.show = show;
        this.seatsBooked = seatsBooked;
        this.userName = userName;
        this.amountPaid = calculateTotal();
    }

    public String getBookingId()          { return bookingId; }
    public Show getShow()                 { return show; }
    public List<ShowSeat> getSeatsBooked(){ return seatsBooked; }
    public double getAmountPaid()         { return amountPaid; }
    public String getUserName()           { return userName; }

    private double calculateTotal() {
        double sum = 0.0;
        for (ShowSeat s : seatsBooked) sum += s.getPrice();
        return sum;
    }
}
