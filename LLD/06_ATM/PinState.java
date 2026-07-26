package atm;

public class PinState implements ATMState {
    private final ATM atm;

    public PinState(ATM atm) {
        this.atm = atm;
    }

    @Override
    public void insertCard(Card card) {
        System.out.println("[PinState] Card already inserted.");
    }

    @Override
    public void enterPin(int pin) {
        Card card = atm.getCurrentCard();
        if (card.validatePin(pin)) {
            System.out.println("[PinState] PIN verified. Welcome!");
            atm.setState(atm.getTransactionState());
        } else {
            System.out.println("[PinState] Incorrect PIN. Ejecting card...");
            ejectCard();
        }
    }

    @Override
    public void checkBalance() {
        System.out.println("[PinState] Enter PIN first.");
    }

    @Override
    public void withdraw(int amount) {
        System.out.println("[PinState] Enter PIN first.");
    }

    @Override
    public void ejectCard() {
        System.out.println("[PinState] Card ejected.");
        atm.setCurrentCard(null);
        atm.setState(atm.getIdleState());
    }
}
