package parkinglot;

import lombok.Getter;
import lombok.Synchronized;
import java.time.LocalDateTime;

@Getter
public class Ticket {
    public enum TicketStatus {
        ACTIVE,
        PAID
    }

    private final String ticketId;
    private final Vehicle vehicle;
    private final ParkingSpot parkingSpot;
    private final String levelId;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private double amount;
    private TicketStatus status;

    public Ticket(String ticketId, Vehicle vehicle, ParkingSpot parkingSpot, String levelId) {
        this.ticketId = ticketId;
        this.vehicle = vehicle;
        this.parkingSpot = parkingSpot;
        this.levelId = levelId;
        this.entryTime = LocalDateTime.now();
        this.status = TicketStatus.ACTIVE;
    }

    @Synchronized
    public void pay(double amount) {
        this.amount = amount;
        this.exitTime = LocalDateTime.now();
        this.status = TicketStatus.PAID;
    }
}
