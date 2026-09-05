package parkinglot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ParkingLot {
    private static ParkingLot instance;
    private final String name;
    private final List<Level> levels;
    private final FeeCalculator feeCalculator;

    private ParkingLot(String name) {
        this.name = name;
        this.levels = new ArrayList<>();
        this.feeCalculator = new HourlyFeeCalculator();
    }

    public String getName() { return name; }

    public static synchronized ParkingLot getInstance(String name) {
        if (instance == null) instance = new ParkingLot(name);
        return instance;
    }

    public synchronized void addLevel(Level level) {
        levels.add(level);
    }

    public synchronized List<Level> getLevels() {
        return new ArrayList<>(levels);
    }

    public synchronized Ticket issueTicket(Vehicle vehicle) {
        for (Level level : levels) {
            ParkingSpot spot = level.parkVehicle(vehicle);
            if (spot != null) {
                String ticketId = "TCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                Ticket ticket = new Ticket(ticketId, vehicle, spot, level.getLevelId());
                System.out.printf("[Gate In] Ticket %s issued for %s (%s) -> Spot %s (%s)%n",
                        ticketId, vehicle.getLicensePlate(), vehicle.getType(), spot.getSpotId(), spot.getType());
                return ticket;
            }
        }
        System.out.printf("[Gate In] Parking Full for %s (%s)!%n", vehicle.getLicensePlate(), vehicle.getType());
        return null;
    }

    public synchronized double releaseVehicle(Ticket ticket, PaymentStrategy paymentStrategy) {
        if (ticket == null) return 0.0;
        if (ticket.getStatus() == Ticket.TicketStatus.PAID) {
            System.out.printf("[Gate Out] Ticket %s has already been paid.%n", ticket.getTicketId());
            return 0.0;
        }

        ParkingSpot spot = ticket.getParkingSpot();
        for (Level level : levels) {
            if (level.getLevelId().equals(ticket.getLevelId())) {
                level.freeSpot(spot);
                break;
            }
        }

        double fee = feeCalculator.calculateFee(ticket.getEntryTime(), java.time.LocalDateTime.now(), ticket.getVehicle().getType());
        paymentStrategy.processPayment(fee);
        ticket.pay(fee);

        System.out.printf("[Gate Out] Vehicle %s exited from Spot %s. Paid $%.2f%n",
                ticket.getVehicle().getLicensePlate(), spot.getSpotId(), fee);
        return fee;
    }
}
