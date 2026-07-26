# 10. Splitwise Bill Splitting (Java LLD Solution)

This folder contains a complete Java implementation of a Splitwise Bill Splitting system.

Below is the **Complete Class Skeleton and API Design** so you can understand the entire system architecture, fields, and method signatures without looking at the source code.

---

## 1. Class Diagram / Architecture Skeleton

### Enums & Users
```java
public enum SplitType { EQUAL, EXACT, PERCENT }

@Getter
@AllArgsConstructor
public class User {
    private final String id;
    private final String name;
    private final String email;
}
```

### Strategy Pattern (Split Calculation Nodes)
```java
@Getter
@Setter
public abstract class Split {
    private final User user;
    protected double amount;

    public Split(User user);
}

class EqualSplit extends Split { ... }
class ExactSplit extends Split { ... }

class PercentSplit extends Split {
    @Getter private final double percent;
}
```

### Expense Validation
```java
@Getter
@AllArgsConstructor
public class Expense {
    private final String id;
    private final User paidBy;
    private final double amount;
    private final SplitType splitType;
    private final List<Split> splits;

    public boolean validate(); // Validates total percent sum = 100% or exact splits sum = amount
}
```

### Splitwise Central Controller (Facade / Debt Simplifier)
```java
public class SplitwiseService {
    @Synchronized public static SplitwiseService getInstance();
    public void addUser(User user);

    @Synchronized public void addExpense(String paidById, double amount, SplitType splitType, List<Split> splits);
    @Synchronized public void showBalances();
    @Synchronized public void simplifyBalances(); // Greedy Heap-based Minimum Cash Flow Algorithm
}
```

---

## 2. Core Workflow & Usage

Here is how to register expenses and simplify debts:

```java
SplitwiseService service = SplitwiseService.getInstance();

User alice = new User("u1", "Alice", "alice@email.com");
User bob = new User("u2", "Bob", "bob@email.com");
User charlie = new User("u3", "Charlie", "charlie@email.com");

service.addUser(alice);
service.addUser(bob);
service.addUser(charlie);

// 1. Alice pays $300 split EQUALLY among Alice, Bob, and Charlie
List<Split> splits1 = Arrays.asList(new EqualSplit(alice), new EqualSplit(bob), new EqualSplit(charlie));
service.addExpense("u1", 300.0, SplitType.EQUAL, splits1); // Bob owes Alice 100, Charlie owes Alice 100

// 2. Bob pays $50 split EXACTLY (Charlie owes 30, Alice owes 20)
List<Split> splits2 = Arrays.asList(new ExactSplit(charlie, 30.0), new ExactSplit(alice, 20.0));
service.addExpense("u2", 50.0, SplitType.EXACT, splits2);

// 3. Show net balances before optimization
service.showBalances();

// 4. Simplify Debts (Min Cash Flow Settlement Plan)
service.simplifyBalances(); // Calculates the minimum transactions needed to clear all debts
```

---

## 3. Concurrency & Thread-Safety Details
- **Safe Ledger Mappings**: Public API entries (`addExpense`, `simplifyBalances`) are fully synchronized (`@Synchronized`) on the `SplitwiseService` singleton. This secures the shared `balanceSheet` map, protecting ledger updates from concurrent modifications.
- **Concurrent Map Objects**: Uses `ConcurrentHashMap` for local balance sheets to support safe concurrent reads.
