package elevator;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class Request {
    private final int floor;
    private final Direction direction; // Used to identify if passenger wants to go UP or DOWN from the floor
}
