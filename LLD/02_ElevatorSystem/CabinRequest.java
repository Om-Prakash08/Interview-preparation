package elevator;

/** A destination selected by a passenger already inside a specific elevator. */
public class CabinRequest extends Request {
    public CabinRequest(int destinationFloor) {
        super(destinationFloor);
    }

    public int getDestinationFloor() { return getFloor(); }
}
