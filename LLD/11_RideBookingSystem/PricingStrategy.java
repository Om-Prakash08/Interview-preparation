package uber;

public interface PricingStrategy {
    double calculateFare(Location start, Location end);
}

class DistancePricingStrategy implements PricingStrategy {
    private final double baseFare;
    private final double perKmRate;

    public DistancePricingStrategy(double baseFare, double perKmRate) {
        this.baseFare = baseFare;
        this.perKmRate = perKmRate;
    }

    @Override
    public double calculateFare(Location start, Location end) {
        double dist = start.distanceTo(end);
        return baseFare + (dist * perKmRate);
    }
}
