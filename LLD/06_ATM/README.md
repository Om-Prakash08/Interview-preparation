# 06. ATM Machine (Java LLD Solution)

This folder contains a complete Java implementation of an ATM Machine System.

Below is the **Complete Class Skeleton and API Design** so you can understand the entire system architecture, fields, and method signatures without looking at the source code.

---

## 1. Class Diagram / Architecture Skeleton

### Data Models
```java
@Getter
@AllArgsConstructor
public class Card {
    private final String cardNumber;
    private final int pin;
    private final String accountNumber;

    public boolean validatePin(int pin);
}

public class Account {
    @Getter private final String accountNumber;
    @Getter private final String ownerName;

    @Synchronized public double getBalance();
    @Synchronized public void debit(double amount);
    @Synchronized public void credit(double amount);
}
```

### State Pattern (ATM Session States)
```java
public interface ATMState {
    void insertCard(Card card);
    void enterPin(int pin);
    void checkBalance();
    void withdraw(int amount);
    void ejectCard();
}

public class IdleState implements ATMState { ... }
public class PinState implements ATMState { ... }
public class TransactionState implements ATMState { ... }
```

### Cash Vault & Bank Controller
```java
public class CashDispenser {
    @Synchronized public boolean canDispense(int amount);
    @Synchronized public void dispense(int amount); // Deducts $100, $50, $20 bills greedily
    @Synchronized public double getTotalCash();
}

public class BankService {
    public void addAccount(Account account);
    public Account getAccount(String accNo);
}

public class ATM {
    @Getter private final BankService bankService;
    @Getter private final CashDispenser cashDispenser;
    @Getter private final ATMState idleState;
    @Getter private final ATMState pinState;
    @Getter private final ATMState transactionState;

    private ATMState currentState;
    private Card currentCard;

    @Synchronized public void setState(ATMState state);
    @Synchronized public ATMState getCurrentState();
    @Synchronized public Card getCurrentCard();
    @Synchronized public void setCurrentCard(Card card);

    // Client wrapper interfaces:
    @Synchronized public void insertCard(Card card);
    @Synchronized public void enterPin(int pin);
    @Synchronized public void checkBalance();
    @Synchronized public void withdraw(int amount);
    @Synchronized public void ejectCard();
}
```

---

## 2. Core Workflow & Usage

Here is how the card authentication and cash withdrawal flow works:

```java
BankService bank = new BankService();
bank.addAccount(new Account("123456789", "John Doe", 2000.0));

CashDispenser dispenser = new CashDispenser(10, 10, 10); // Ten of each: $100, $50, $20
ATM atm = new ATM(bank, dispenser);

// 1. Insert Card (Idle -> PinState)
Card card = new Card("CARD-9876", 1234, "123456789");
atm.insertCard(card);

// 2. Validate PIN (PinState -> TransactionState)
atm.enterPin(1234);

// 3. Withdraw money (TransactionState -> IdleState)
atm.withdraw(270); // Dispenses 2x$100, 1x$50, 1x$20
```

---

## 3. Concurrency & Thread-Safety Details
- **Session Locking**: All user-facing methods in `ATM` are synchronized (`@Synchronized`), preventing race conditions where multiple requests attempt to eject card, check balance, or withdraw cash concurrently.
- **Account Ledger Safety**: Account balance mutations (`debit`, `credit`) are fully thread-safe (`@Synchronized`), ensuring correct updates across multiple concurrent ATM endpoints.
- **Vault Protection**: `CashDispenser` synchronizes bill mix checks and deductions to prevent concurrent withdrawals from double-claiming the same physical bills.
