package splitwise;

import java.util.ArrayList;
import java.util.List;

public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FAANG System Design Demo: Splitwise Bill Splitting ===");

        SplitwiseService splitwise = SplitwiseService.getInstance();

        // 1. Add Users
        User u1 = new User("u1", "Alice", "alice@example.com");
        User u2 = new User("u2", "Bob", "bob@example.com");
        User u3 = new User("u3", "Charlie", "charlie@example.com");
        User u4 = new User("u4", "David", "david@example.com");

        splitwise.addUser(u1);
        splitwise.addUser(u2);
        splitwise.addUser(u3);
        splitwise.addUser(u4);

        System.out.println("Added users: Alice, Bob, Charlie, David.");

        // 2. Scenario 1: Equal Split
        // Alice pays $100 for all 4 users
        System.out.println("\n--- Expense 1: Alice pays $100 split EQUALLY among all 4 ---");
        List<Split> splits1 = new ArrayList<>();
        splits1.add(new EqualSplit(u1));
        splits1.add(new EqualSplit(u2));
        splits1.add(new EqualSplit(u3));
        splits1.add(new EqualSplit(u4));
        splitwise.addExpense("u1", 100.0, SplitType.EQUAL, splits1);

        splitwise.showBalances();

        // 3. Scenario 2: Exact Split
        // Bob pays $50 split between Charlie ($30) and David ($20)
        System.out.println("\n--- Expense 2: Bob pays $50 split EXACTLY (Charlie: $30, David: $20) ---");
        List<Split> splits2 = new ArrayList<>();
        splits2.add(new ExactSplit(u3, 30.0));
        splits2.add(new ExactSplit(u4, 20.0));
        splitwise.addExpense("u2", 50.0, SplitType.EXACT, splits2);

        splitwise.showBalances();

        // 4. Scenario 3: Percent Split
        // Charlie pays $80 split (Alice: 40%, Bob: 40%, Charlie: 20%)
        System.out.println("\n--- Expense 3: Charlie pays $80 split by PERCENT (Alice: 40%, Bob: 40%, Charlie: 20%) ---");
        List<Split> splits3 = new ArrayList<>();
        splits3.add(new PercentSplit(u1, 40.0));
        splits3.add(new PercentSplit(u2, 40.0));
        splits3.add(new PercentSplit(u3, 20.0));
        splitwise.addExpense("u3", 80.0, SplitType.PERCENT, splits3);

        splitwise.showBalances();

        // 5. Simplify Debts
        splitwise.simplifyBalances();

        System.out.println("\n=== Splitwise Demo Finished successfully ===");
    }
}
