package elevator;

/** A request made from a floor landing to travel in a particular direction. */
public class HallRequest extends Request {
    private final Direction direction;

    public HallRequest(int pickupFloor, Direction direction) {
        super(pickupFloor);
        if (direction == Direction.IDLE) {
            throw new IllegalArgumentException("A hall request must specify UP or DOWN");
        }
        this.direction = direction;
    }

    public int getPickupFloor() { return getFloor(); }
    public Direction getDirection() { return direction; }
}
