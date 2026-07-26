package hotel;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Guest {
    private final String name;
    private final String email;
    private final String phone;
}
