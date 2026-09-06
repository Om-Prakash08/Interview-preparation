package uber;

public class Location {
    private final double x;
    private final double y;

    public Location(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() { return x; }
    public double getY() { return y; }

    public double distanceTo(Location other) {
        double xDiff = this.x - other.x;
        double yDiff = this.y - other.y;
        return Math.sqrt(xDiff * xDiff + yDiff * yDiff);
    }
}
