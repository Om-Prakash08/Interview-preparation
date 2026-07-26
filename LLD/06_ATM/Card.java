package atm;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class Card {
    private final String cardNumber;
    private final int pin;
    private final String accountNumber;

    public boolean validatePin(int pin) {
        return this.pin == pin;
    }
}
