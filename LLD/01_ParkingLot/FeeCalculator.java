package parkinglot;

import java.time.Duration;
import java.time.LocalDateTime;

public interface FeeCalculator {
    double calculateFee(LocalDateTime entryTime, LocalDateTime exitTime, VehicleType vehicleType);
}

class HourlyFeeCalculator implements FeeCalculator {
    @Override
    public double calculateFee(LocalDateTime entryTime, LocalDateTime exitTime, VehicleType vehicleType) {
        long minutes = Duration.between(entryTime, exitTime).toMinutes();
        // Charge minimum 1 hour, otherwise round up to nearest hour
        double hours = Math.max(1.0, Math.ceil(minutes / 60.0));
        
        double rate;
        switch (vehicleType) {
            case MOTORCYCLE:
                rate = 1.00;
                break;
            case CAR:
                rate = 2.00;
                break;
            case TRUCK:
            case VAN:
            default:
                rate = 4.00;
                break;
        }
        return hours * rate;
    }
}
