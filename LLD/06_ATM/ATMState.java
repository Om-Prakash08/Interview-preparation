package atm;

public interface ATMState {
    void insertCard(Card card);
    void enterPin(int pin);
    void checkBalance();
    void withdraw(int amount);
    void ejectCard();
}
