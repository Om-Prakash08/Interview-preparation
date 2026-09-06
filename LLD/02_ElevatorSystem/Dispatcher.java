package elevator;

import java.util.List;

/**
 * @deprecated Renamed to {@link ElevatorController}.
 */
@Deprecated
public class Dispatcher extends ElevatorController {
    public Dispatcher(List<Elevator> elevators) {
        super(elevators);
    }
}
