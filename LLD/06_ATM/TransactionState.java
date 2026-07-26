package atm;

public class TransactionState implements ATMState {
    private final ATM atm;

    public TransactionState(ATM atm) {
        this.atm = atm;
    }

    @Override
    public void insertCard(Card card) {
        System.out.println("[TransactionState] Card already inserted.");
    }

    @Override
    public void enterPin(int pin) {
        System.out.println("[TransactionState] PIN already verified.");
    }

    @Override
    public void checkBalance() {
        Card card = atm.getCurrentCard();
        Account account = atm.getBankService().getAccount(card.getAccountNumber());
        System.out.printf("[Balance] Account: %s. Balance: $%.2f%n", account.getAccountNumber(), account.getBalance());
    }

    @Override
    public void withdraw(int amount) {
        Card card = atm.getCurrentCard();
        Account account = atm.getBankService().getAccount(card.getAccountNumber());
        
        if (account.getBalance() < amount) {
            System.out.printf("[Withdrawal Failed] Insufficient balance. Account: $%.2f, Request: %d%n",
                    account.getBalance(), amount);
            return;
        }

        CashDispenser dispenser = atm.getCashDispenser();
        if (dispenser.getTotalAvailableCash() < amount) {
            System.out.println("[Withdrawal Failed] ATM has insufficient cash inventory.");
            return;
        }

        if (!dispenser.canDispenseAmount(amount)) {
            System.out.printf("[Withdrawal Failed] ATM cannot dispense the requested amount (%d) with current bill mix.%n", amount);
            return;
        }

        // Dispense and debit
        dispenser.dispense(amount);
        account.debit(amount);
        System.out.printf("[Withdrawal Success] Dispensed $%d. New account balance: $%.2f%n", amount, account.getBalance());
    }

    @Override
    public void ejectCard() {
        System.out.println("[TransactionState] Card ejected. Thank you!");
        atm.setCurrentCard(null);
        atm.setState(atm.getIdleState());
    }
}
