package parkinglot;

import java.time.LocalDateTime;

public class Ticket {
    public enum TicketStatus { ACTIVE, PAID }

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

    public String getTicketId()           { return ticketId; }
    public Vehicle getVehicle()           { return vehicle; }
    public ParkingSpot getParkingSpot()   { return parkingSpot; }
    public String getLevelId()            { return levelId; }
    public LocalDateTime getEntryTime()   { return entryTime; }
    public LocalDateTime getExitTime()    { return exitTime; }
    public double getAmount()             { return amount; }
    public TicketStatus getStatus()       { return status; }

    public synchronized void pay(double amount) {
        this.amount = amount;
        this.exitTime = LocalDateTime.now();
        this.status = TicketStatus.PAID;
    }
}
