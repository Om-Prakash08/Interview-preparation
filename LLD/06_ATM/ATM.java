package atm;

public class ATM {
    private final BankService bankService;
    private final CashDispenser cashDispenser;
    private final ATMState idleState;
    private final ATMState pinState;
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

    public BankService getBankService()       { return bankService; }
    public CashDispenser getCashDispenser()   { return cashDispenser; }
    public ATMState getIdleState()            { return idleState; }
    public ATMState getPinState()             { return pinState; }
    public ATMState getTransactionState()     { return transactionState; }

    public synchronized void setState(ATMState state)    { this.currentState = state; }
    public synchronized ATMState getCurrentState()       { return currentState; }
    public synchronized Card getCurrentCard()            { return currentCard; }
    public synchronized void setCurrentCard(Card card)   { this.currentCard = card; }

    public synchronized void insertCard(Card card) { currentState.insertCard(card); }
    public synchronized void enterPin(int pin)     { currentState.enterPin(pin); }
    public synchronized void checkBalance()        { currentState.checkBalance(); }
    public synchronized void withdraw(int amount)  { currentState.withdraw(amount); }
    public synchronized void ejectCard()           { currentState.ejectCard(); }
}
