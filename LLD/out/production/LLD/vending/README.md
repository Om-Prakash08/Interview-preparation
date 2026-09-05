# 05. Vending Machine — LLD Interview Guide

> **Framework:** Requirements → Entities → Class Design → Implementation → Extensibility

---

## ① Requirements (Clarify First!)

> *"Before I write any code, let me confirm the scope."*

**Functional Requirements:**
- Accept coins (Nickel, Dime, Quarter, Dollar)
- Display product catalog with codes and prices
- Select a product by code; validate sufficient deposit
- Dispense product and return change
- Cancel transaction at any time and refund coins
- Admin can restock products

**Non-Functional Requirements:**
- **Thread-safe**: Physical machines may have concurrent button presses
- Machine behavior changes based on internal **state** — use State Pattern
- Prevent double-dispensing under concurrent operations

**Out of Scope:**
- Card / contactless payments (can extend via new State)
- Network-connected machine management

---

## ② Entities (Nouns → Classes)

> *"I'll identify the key nouns to define my classes."*

| Entity | Type | Responsibility |
|---|---|---|
| `Coin` | Enum | `NICKEL(0.05), DIME(0.10), QUARTER(0.25), DOLLAR(1.00)` |
| `Product` | Class | Name + price |
| `Inventory` | Class | Map of product codes → (Product, quantity) |
| `State` | Interface | Actions: `insertCoin`, `selectProduct`, `dispenseProduct`, `refund` |
| `IdleState` | Concrete State | No coin inserted; ready to accept coins |
| `HasMoneyState` | Concrete State | Coin(s) inserted; waiting for product selection |
| `DispenseState` | Concrete State | Product selected & deposit sufficient; ready to dispense |
| `VendingMachine` | Context | Holds current state; exposes synchronized client API |

---

## ③ Class Design (Design Patterns)

> *"I'll highlight the design patterns used and why."*

### 🔷 State Pattern — Core Design
```
State (interface)
    ├── IdleState       → accepts coins; ignores other actions
    ├── HasMoneyState   → selects product; returns change on cancel
    └── DispenseState   → dispenses product; returns to IdleState

VendingMachine (Context)
    ├── holds: idleState, hasMoneyState, dispenseState
    ├── currentState → switches between above
    ├── deposit (double)
    └── selectedProductCode (String)
```
**Why?** Without State Pattern, you'd have massive `if-else` chains checking machine mode in every method. State Pattern makes each mode's behavior self-contained and independently testable.

### 🔷 Class Skeleton
```java
public enum Coin {
    NICKEL(0.05), DIME(0.10), QUARTER(0.25), DOLLAR(1.00);
    private final double value;
}

public interface State {
    void insertCoin(Coin coin);
    void selectProduct(String code);
    void dispenseProduct();
    void refund();
}

public class IdleState     implements State { ... }
public class HasMoneyState implements State { ... }
public class DispenseState implements State { ... }

public class Inventory {
    public void addProduct(String code, Product product, int quantity);
    public boolean hasProduct(String code);
    public void deductQuantity(String code);
}

public class VendingMachine {
    private State currentState;
    private double deposit;
    private String selectedProductCode;

    @Synchronized public void insertCoin(Coin coin);       // Delegates to currentState
    @Synchronized public void selectProduct(String code);  // Delegates to currentState
    @Synchronized public void dispense();                  // Delegates to currentState
    @Synchronized public void cancelTransaction();         // Delegates to currentState
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Happy Path (Buy a Product)
```java
VendingMachine machine = new VendingMachine();

// Setup
machine.getInventory().addProduct("A1", new Product("Coke", 1.50), 5);

// Step 1: Insert coins → IdleState → HasMoneyState
machine.insertCoin(Coin.DOLLAR);   // deposit = 1.00
machine.insertCoin(Coin.QUARTER);  // deposit = 1.25
machine.insertCoin(Coin.QUARTER);  // deposit = 1.50

// Step 2: Select product → HasMoneyState → DispenseState
machine.selectProduct("A1");
// Checks: deposit (1.50) >= product price (1.50) ✓
// Transitions to DispenseState

// Step 3: Dispense → DispenseState → IdleState
machine.dispense();
// Deducts inventory, returns change (0.00), resets deposit, back to Idle
```

### Cancel / Refund Path
```java
machine.insertCoin(Coin.DOLLAR);  // deposit = 1.00
machine.cancelTransaction();
// → HasMoneyState.refund() → prints "Returning $1.00" → resets → IdleState
```

### State Transition Diagram
```
[IdleState] ──insertCoin()──► [HasMoneyState] ──selectProduct() & deposit≥price──► [DispenseState]
    ▲                               │                                                      │
    │                         cancelTransaction()                                    dispense()
    │                               │                                                      │
    └───────────────────────────────┴──────────────────────────────────────────────────────┘
```

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| Card payment support | Add `CardPaymentState`, transition after `insertCard()` |
| Display screen messages | Add `display(String msg)` to `VendingMachine`, call from each State |
| Low stock alerts | Add observer in `Inventory.deductQuantity()` |
| Multiple product rows | Extend `Inventory` to support row/column codes (A1-D9) |
| Admin restock mode | Add `AdminState implements State` |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `VendingMachine.insertCoin` | `@Synchronized` | Prevent two threads adding coins simultaneously |
| `VendingMachine.dispense` | `@Synchronized` | Prevent double-dispense race condition |
| `VendingMachine.selectProduct` | `@Synchronized` | Atomic deposit check + state transition |
| `Inventory.deductQuantity` | Called inside synchronized context | Safe stock decrement |
