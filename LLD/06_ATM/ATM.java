package atm;

import lombok.Getter;
import lombok.Synchronized;

public class ATM {
    @Getter
    private final BankService bankService;
    @Getter
    private final CashDispenser cashDispenser;
    @Getter
    private final ATMState idleState;
    @Getter
    private final ATMState pinState;
    @Getter
    private final ATMState transactionState;

    private ATMState currentState;
    private Card currentCard;

    public ATM(BankService bankService, CashDispenser cashDispenser) {
        this.bankService = bankService;
        this.cashDispenser = cashDispenser;
        this.idleState = new IdleState(this);
        this.pinState = new PinState(this);
        this.transactionState = new TransactionState(this);
        this.currentState = idleState;
    }

    @Synchronized
    public void setState(ATMState state) {
        this.currentState = state;
    }

    @Synchronized
    public ATMState getCurrentState() {
        return currentState;
    }

    @Synchronized
    public Card getCurrentCard() {
        return currentCard;
    }

    @Synchronized
    public void setCurrentCard(Card card) {
        this.currentCard = card;
    }

    @Synchronized
    public void insertCard(Card card) {
        currentState.insertCard(card);
    }

    @Synchronized
    public void enterPin(int pin) {
        currentState.enterPin(pin);
    }

    @Synchronized
    public void checkBalance() {
        currentState.checkBalance();
    }

    @Synchronized
    public void withdraw(int amount) {
        currentState.withdraw(amount);
    }

    @Synchronized
    public void ejectCard() {
        currentState.ejectCard();
    }
}
