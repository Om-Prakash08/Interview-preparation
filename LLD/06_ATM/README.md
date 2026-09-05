# 06. ATM Machine — LLD Interview Guide

> **Framework:** Requirements → Entities → Class Design → Implementation → Extensibility

---

## ① Requirements (Clarify First!)

> *"Before I write any code, let me confirm the scope."*

**Functional Requirements:**
- Insert card and validate PIN
- Check account balance
- Withdraw cash (ATM dispenses denominations: $100, $50, $20)
- Eject card (end session)
- Cash vault tracks available bills

**Non-Functional Requirements:**
- **Thread-safe**: Multiple ATMs sharing the same bank accounts
- Each ATM session has clear **states** — use State Pattern
- Vault must prevent two concurrent withdrawals from claiming the same bills

**Out of Scope:**
- Deposit cash
- Transfer between accounts
- Card blocking after wrong PIN retries (can extend)

---

## ② Entities (Nouns → Classes)

> *"I'll identify the key nouns to define my classes."*

| Entity | Type | Responsibility |
|---|---|---|
| `Card` | Class | cardNumber, PIN (hashed), accountNumber |
| `Account` | Class | accountNumber, owner, balance |
| `BankService` | Class | Account registry (in-memory bank) |
| `CashDispenser` | Class | Vault with $100/$50/$20 bill counts; greedy dispense |
| `ATMState` | Interface | Session actions: insertCard, enterPin, checkBalance, withdraw, ejectCard |
| `IdleState` | State | No card; waits for insertion |
| `PinState` | State | Card inserted; waits for PIN |
| `TransactionState` | State | Authenticated; ready for operations |
| `ATM` | Context | Holds currentState, currentCard; synchronized client API |

---

## ③ Class Design (Design Patterns)

> *"I'll highlight the design patterns used and why."*

### 🔷 State Pattern — ATM Session Lifecycle
```
ATMState (interface)
    ├── IdleState        → only insertCard() works; others throw "Invalid Operation"
    ├── PinState         → only enterPin() and ejectCard() work
    └── TransactionState → checkBalance, withdraw, ejectCard available

ATM (Context)
    ├── currentState: ATMState
    ├── currentCard: Card
    ├── bankService: BankService
    └── cashDispenser: CashDispenser
```
**Why?** Without State Pattern, every method would need `if (currentState == IDLE)` guards. State Pattern localizes behavior per session phase.

### 🔷 Chain of Responsibility — Cash Dispense (Greedy)
```
withdraw(270):
  → try $100 bills first → dispense 2 ($200 remaining: $70)
  → try $50 bills → dispense 1 ($50 remaining: $20)
  → try $20 bills → dispense 1 ✓
```

### 🔷 Class Skeleton
```java
public class Card {
    private final String cardNumber;
    private final int pin;
    private final String accountNumber;

    public boolean validatePin(int pin);
}

public class Account {
    @Synchronized public double getBalance();
    @Synchronized public void debit(double amount);   // Validates sufficient funds
    @Synchronized public void credit(double amount);
}

public interface ATMState {
    void insertCard(Card card);
    void enterPin(int pin);
    void checkBalance();
    void withdraw(int amount);
    void ejectCard();
}

public class CashDispenser {
    @Synchronized public boolean canDispense(int amount); // Checks bill availability
    @Synchronized public void dispense(int amount);       // Greedy: $100 → $50 → $20
    @Synchronized public double getTotalCash();
}

public class ATM {
    private ATMState currentState;
    private Card currentCard;

    @Synchronized public void insertCard(Card card);  // Delegates to currentState
    @Synchronized public void enterPin(int pin);      // Delegates to currentState
    @Synchronized public void checkBalance();         // Delegates to currentState
    @Synchronized public void withdraw(int amount);   // Delegates to currentState
    @Synchronized public void ejectCard();            // Delegates to currentState
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Setup
```java
BankService bank = new BankService();
bank.addAccount(new Account("123456789", "John Doe", 2000.0));

CashDispenser dispenser = new CashDispenser(10, 10, 10); // 10×$100, 10×$50, 10×$20
ATM atm = new ATM(bank, dispenser);
```

### Session Flow
```java
// Step 1: Insert Card → IdleState → PinState
Card card = new Card("CARD-9876", 1234, "123456789");
atm.insertCard(card);

// Step 2: Enter PIN → PinState → TransactionState
atm.enterPin(1234);
// → card.validatePin(1234) ✓ → setState(transactionState)

// Step 3: Check Balance (Optional)
atm.checkBalance();
// → bank.getAccount("123456789").getBalance() → prints $2000.00

// Step 4: Withdraw → TransactionState
atm.withdraw(270);
// → canDispense(270) ✓ → account.debit(270) → dispense(270)
// → Dispenses: 2×$100 + 1×$50 + 1×$20

// Step 5: Eject Card → back to IdleState
atm.ejectCard();
```

### State Transition Diagram
```
[IdleState] ──insertCard()──► [PinState] ──enterPin() OK──► [TransactionState]
    ▲                              │                                  │
    │                         ejectCard()                   ejectCard() / withdraw()
    └──────────────────────────────┴──────────────────────────────────┘
```

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| PIN retry limit (block after 3 fails) | Add `retryCount` to `PinState`; transition to `BlockedState` |
| Deposit cash | Add `depositCash()` to `ATMState`; implement in `TransactionState` |
| Contactless / NFC card | Extend `Card` → `NfcCard`; ATM checks card type before PIN |
| Multi-currency support | Add `Currency` enum to `Account` and `CashDispenser` |
| Receipt printing | Add `ReceiptPrinter` dependency to `ATM`, call after transaction |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `ATM.withdraw / enterPin` | `@Synchronized` | Prevent concurrent sessions on same ATM instance |
| `Account.debit / credit` | `@Synchronized` | Correct balance across multiple ATM endpoints |
| `CashDispenser.dispense` | `@Synchronized` | Prevent two withdrawals claiming same bills |
| `CashDispenser.canDispense` | `@Synchronized` | Consistent check-before-dispense atomicity |
