package atm;

public class IdleState implements ATMState {
    private final ATM atm;

    public IdleState(ATM atm) {
        this.atm = atm;
    }

    @Override
    public void insertCard(Card card) {
        atm.setCurrentCard(card);
        System.out.printf("[Idle] Card %s inserted. Please enter PIN.%n", card.getCardNumber());
        atm.setState(atm.getPinState());
    }

    @Override
    public void enterPin(int pin) {
        System.out.println("[Idle] Insert card first.");
    }

    @Override
    public void checkBalance() {
        System.out.println("[Idle] Insert card first.");
    }

    @Override
    public void withdraw(int amount) {
        System.out.println("[Idle] Insert card first.");
    }

    @Override
    public void ejectCard() {
        System.out.println("[Idle] No card in machine.");
    }
}
