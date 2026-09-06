package elevator;

/** Base type for an elevator request. */
public abstract class Request {
    private final int floor;

    protected Request(int floor) {
        this.floor = floor;
    }

    public int getFloor() { return floor; }
}
