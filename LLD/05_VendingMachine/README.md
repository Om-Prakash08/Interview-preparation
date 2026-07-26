# 05. Vending Machine (Java LLD Solution)

This folder contains a complete Java implementation of a Vending Machine System.

Below is the **Complete Class Skeleton and API Design** so you can understand the entire system architecture, fields, and method signatures without looking at the source code.

---

## 1. Class Diagram / Architecture Skeleton

### Data Models & Enums
```java
@Getter
@AllArgsConstructor
public enum Coin {
    NICKEL(0.05), DIME(0.10), QUARTER(0.25), DOLLAR(1.00);
    private final double value;
}

@Getter
@AllArgsConstructor
public class Product {
    private final String name;
    private final double price;
}
```

### State Design Pattern (State Interfaces & Concrete States)
```java
public interface State {
    void insertCoin(Coin coin);
    void selectProduct(String code);
    void dispenseProduct();
    void refund();
}

public class IdleState implements State { ... }
public class HasMoneyState implements State { ... }
public class DispenseState implements State { ... }
```

### Inventory & Context (Vending Machine)
```java
public class Inventory {
    public void addProduct(String code, Product product, int quantity);
    public Product getProduct(String code);
    public int getQuantity(String code);
    public void deductQuantity(String code);
    public boolean hasProduct(String code);
}

public class VendingMachine {
    @Getter private final Inventory inventory;
    @Getter private final State idleState;
    @Getter private final State hasMoneyState;
    @Getter private final State dispenseState;

    private State currentState;
    private double deposit = 0.0;
    private String selectedProductCode = null;

    @Synchronized public void setState(State state);
    @Synchronized public State getCurrentState();
    @Synchronized public double getDeposit();
    @Synchronized public void addDeposit(double amount);
    @Synchronized public void clearDeposit();
    @Synchronized public String getSelectedProductCode();
    @Synchronized public void setSelectedProductCode(String code);

    // Client delegates (Thread-Safe Wrapper Interface)
    @Synchronized public void insertCoin(Coin coin);
    @Synchronized public void selectProduct(String code);
    @Synchronized public void dispense();
    @Synchronized public void cancelTransaction();
}
```

---

## 2. Core Workflow & Usage

Here is how the State Pattern handles machine operations:

```java
VendingMachine machine = new VendingMachine();

// 1. Setup Inventory
Product coke = new Product("Coke", 1.50);
machine.getInventory().addProduct("A1", coke, 5);

// 2. Insert cash (Idle -> HasMoneyState)
machine.insertCoin(Coin.DOLLAR);
machine.insertCoin(Coin.QUARTER);
machine.insertCoin(Coin.QUARTER); // Deposit: $1.50

// 3. Select Product (HasMoneyState -> DispenseState)
machine.selectProduct("A1");

// 4. Dispense Product (DispenseState -> IdleState)
machine.dispense();
```

---

## 3. Concurrency & Thread-Safety Details
- **Double Dispensing Prevention**: The client-facing delegates in `VendingMachine` (`insertCoin`, `selectProduct`, `dispense`, `cancelTransaction`) are fully synchronized (`@Synchronized`), ensuring that multiple users pressing buttons concurrently do not double-dispense items or trigger concurrent refund allocations.
- **Inventory Locking**: Modifying quantities and checking stock levels are safeguarded inside the synchronized inventory context.
