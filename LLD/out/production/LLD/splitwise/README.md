# 10. Splitwise Bill Splitting — LLD Interview Guide

> **Framework:** Requirements → Entities → Class Design → Implementation → Extensibility

---

## ① Requirements (Clarify First!)

> *"Before I write any code, let me confirm the scope."*

**Functional Requirements:**
- Users can add **expenses** paid by one user on behalf of a group
- Three split types:
  - **EQUAL** — divide equally among participants
  - **EXACT** — each participant owes a specific amount
  - **PERCENT** — each participant owes a percentage (must sum to 100%)
- Show **current balances** between all users
- **Simplify debts** — compute minimum number of transactions to settle all balances

**Non-Functional Requirements:**
- **Thread-safe**: Concurrent expense additions from multiple devices
- **Singleton** service — single source of truth for the balance sheet

**Out of Scope:**
- Actual payment processing
- Group management (named groups)
- Currency conversion

---

## ② Entities (Nouns → Classes)

> *"I'll identify the key nouns to define my classes."*

| Entity | Type | Responsibility |
|---|---|---|
| `SplitType` | Enum | `EQUAL, EXACT, PERCENT` |
| `User` | Class | id, name, email |
| `Split` | Abstract | Base: user + amount |
| `EqualSplit` | Concrete | Amount set by service at runtime |
| `ExactSplit` | Concrete | Amount provided upfront |
| `PercentSplit` | Concrete | Percent provided; amount calculated by service |
| `Expense` | Class | paidBy, amount, splitType, list of Splits; validate() |
| `SplitwiseService` | Singleton | Balance sheet ledger, addExpense, simplifyBalances |

---

## ③ Class Design (Design Patterns)

> *"I'll highlight the design patterns used and why."*

### 🔷 Strategy Pattern — Split Hierarchy
```
Split (abstract)
    ├── EqualSplit   → amount = totalAmount / participants.size()
    ├── ExactSplit   → amount = userProvided
    └── PercentSplit → amount = totalAmount × (percent / 100)
```
**Why?** Adding a new split type (e.g., `ProportionalSplit`) only requires creating a new subclass — no changes to `SplitwiseService`.

### 🔷 Balance Sheet — Nested Map (Key Data Structure)
```
Map<String userId, Map<String oweToUserId, Double amount>>
```
**Example:**
```
alice → { bob: -100 }  (alice owes bob $100)
bob   → { alice: 100 } (bob is owed $100 by alice)
```

### 🔷 Simplify Debts — Min Cash Flow (Greedy Algorithm)
```
1. Compute net balance for each user: sum of all they're owed - all they owe
2. Use two heaps (max-heap for creditors, max-heap for debtors)
3. Greedily match biggest debtor with biggest creditor
4. Create transaction, update balances, repeat until all settled
```

### 🔷 Class Skeleton
```java
public abstract class Split {
    private final User user;
    protected double amount;
}

public class Expense {
    private final User paidBy;
    private final double amount;
    private final SplitType splitType;
    private final List<Split> splits;

    public boolean validate();
    // EQUAL: sum(splits.amount) == amount
    // EXACT: sum(splits.amount) == amount
    // PERCENT: sum(percentSplit.percent) == 100
}

public class SplitwiseService {
    @Synchronized public static SplitwiseService getInstance();
    public void addUser(User user);

    @Synchronized public void addExpense(String paidById, double amount,
        SplitType splitType, List<Split> splits);
    // → creates Expense, validates, updates balanceSheet

    @Synchronized public void showBalances();
    @Synchronized public void simplifyBalances(); // Greedy Min Cash Flow
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Setup
```java
SplitwiseService service = SplitwiseService.getInstance();

User alice   = new User("u1", "Alice",   "alice@email.com");
User bob     = new User("u2", "Bob",     "bob@email.com");
User charlie = new User("u3", "Charlie", "charlie@email.com");

service.addUser(alice);
service.addUser(bob);
service.addUser(charlie);
```

### Add EQUAL Expense
```java
// Alice pays $300, split equally among all 3
List<Split> splits = Arrays.asList(
    new EqualSplit(alice), new EqualSplit(bob), new EqualSplit(charlie)
);
service.addExpense("u1", 300.0, SplitType.EQUAL, splits);
// Service sets each split.amount = 300/3 = $100
// Balance: bob owes alice $100, charlie owes alice $100
```

### Add EXACT Expense
```java
// Bob pays $50: charlie owes $30, alice owes $20
List<Split> splits2 = Arrays.asList(
    new ExactSplit(charlie, 30.0), new ExactSplit(alice, 20.0)
);
service.addExpense("u2", 50.0, SplitType.EXACT, splits2);
// Net: alice owes bob $20, charlie owes alice $100, charlie owes bob $30
```

### Show & Simplify
```java
service.showBalances();
// Alice: owes Bob $20, is owed by Charlie $100
// Bob: is owed $20 by Alice, is owed $30 by Charlie
// Charlie: owes Alice $100, owes Bob $30

service.simplifyBalances();
// Minimum transactions computed:
// Charlie pays Alice $80
// Charlie pays Bob $50
// (Reduces 4 transactions → 2)
```

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| New split type (proportional by weight) | Extend `Split` → `WeightedSplit`, add `WEIGHTED` to `SplitType` |
| Named groups | Add `Group` entity; `SplitwiseService.addGroupExpense()` |
| Expense categories (food, travel) | Add `Category` enum to `Expense` |
| Notification on debt | Add `NotificationService` called after `addExpense()` |
| Currency support | Add `Currency` field to `Expense` and conversion in balance sheet |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `addExpense()` | `@Synchronized` | Prevent concurrent ledger write race on `balanceSheet` map |
| `simplifyBalances()` | `@Synchronized` | Prevent partial reads during multi-user balance computation |
| `balanceSheet` | `ConcurrentHashMap` | Safe concurrent reads between synchronized writes |
