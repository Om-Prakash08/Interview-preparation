package splitwise;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SplitwiseService {
    private static SplitwiseService instance;
    private final Map<String, User> users;
    private final List<Expense> expenses;
    private final Map<String, Map<String, Double>> balanceSheet;

    private SplitwiseService() {
        this.users = new ConcurrentHashMap<>();
        this.expenses = new ArrayList<>();
        this.balanceSheet = new ConcurrentHashMap<>();
    }

    public static synchronized SplitwiseService getInstance() {
        if (instance == null) instance = new SplitwiseService();
        return instance;
    }

    public void addUser(User user) {
        users.put(user.getId(), user);
        balanceSheet.put(user.getId(), new ConcurrentHashMap<>());
    }

    public synchronized void addExpense(String paidById, double amount, SplitType splitType, List<Split> splits) {
        User paidBy = users.get(paidById);
        if (paidBy == null) return;

        if (splitType == SplitType.EQUAL) {
            double splitAmount = amount / splits.size();
            for (Split s : splits) s.setAmount(splitAmount);
        } else if (splitType == SplitType.PERCENT) {
            for (Split s : splits) s.setAmount((((PercentSplit) s).getPercent() * amount) / 100.0);
        }

        Expense expense = new Expense("EXP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                paidBy, amount, splitType, splits);
        if (!expense.validate()) {
            System.out.println("[Splitwise] Validation failed for expense split. Ignoring.");
            return;
        }
        expenses.add(expense);

        for (Split split : splits) {
            String debtorId = split.getUser().getId();
            if (debtorId.equals(paidById)) continue;
            updateBalance(debtorId, paidById, split.getAmount());
        }
        System.out.printf("[Expense Added] %s paid $%.2f. Split type: %s%n", paidBy.getName(), amount, splitType);
    }

    private void updateBalance(String debtor, String creditor, double amount) {
        Map<String, Double> creditorBalances = balanceSheet.get(creditor);
        double credOwesDeb = creditorBalances.getOrDefault(debtor, 0.0);
        if (credOwesDeb >= amount) {
            creditorBalances.put(debtor, credOwesDeb - amount);
        } else {
            creditorBalances.put(debtor, 0.0);
            double netDebt = amount - credOwesDeb;
            Map<String, Double> debtorBalances = balanceSheet.get(debtor);
            debtorBalances.put(creditor, debtorBalances.getOrDefault(creditor, 0.0) + netDebt);
        }
    }

    public synchronized void showBalances() {
        System.out.println("\n--- Current Balance Sheet ---");
        boolean hasBalances = false;
        for (Map.Entry<String, Map<String, Double>> entry1 : balanceSheet.entrySet()) {
            User debtor = users.get(entry1.getKey());
            for (Map.Entry<String, Double> entry2 : entry1.getValue().entrySet()) {
                double amount = entry2.getValue();
                if (amount > 0.01) {
                    System.out.printf("%s owes %s: $%.2f%n", debtor.getName(), users.get(entry2.getKey()).getName(), amount);
                    hasBalances = true;
                }
            }
        }
        if (!hasBalances) System.out.println("All accounts are settled.");
    }

    public synchronized void simplifyBalances() {
        System.out.println("\n--- Simplified Debt Settlement Plan (Min Cash Flow) ---");
        Map<String, Double> netBalances = new HashMap<>();
        for (String userId : users.keySet()) netBalances.put(userId, 0.0);
        for (Map.Entry<String, Map<String, Double>> entry1 : balanceSheet.entrySet()) {
            String debtorId = entry1.getKey();
            for (Map.Entry<String, Double> entry2 : entry1.getValue().entrySet()) {
                double amount = entry2.getValue();
                if (amount > 0.01) {
                    netBalances.put(debtorId, netBalances.get(debtorId) - amount);
                    netBalances.put(entry2.getKey(), netBalances.get(entry2.getKey()) + amount);
                }
            }
        }
        PriorityQueue<UserBalance> debtors = new PriorityQueue<>(Comparator.comparingDouble(x -> x.balance));
        PriorityQueue<UserBalance> creditors = new PriorityQueue<>((x, y) -> Double.compare(y.balance, x.balance));
        for (Map.Entry<String, Double> entry : netBalances.entrySet()) {
            double balance = entry.getValue();
            if (balance < -0.01) debtors.add(new UserBalance(entry.getKey(), balance));
            else if (balance > 0.01) creditors.add(new UserBalance(entry.getKey(), balance));
        }
        if (debtors.isEmpty() && creditors.isEmpty()) { System.out.println("No payments required. All settled."); return; }
        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            UserBalance debtor = debtors.poll(), creditor = creditors.poll();
            double amt = Math.min(-debtor.balance, creditor.balance);
            System.out.printf("[Settlement] %s pays %s: $%.2f%n", users.get(debtor.userId).getName(), users.get(creditor.userId).getName(), amt);
            debtor.balance += amt; creditor.balance -= amt;
            if (debtor.balance < -0.01) debtors.add(debtor);
            if (creditor.balance > 0.01) creditors.add(creditor);
        }
    }

    private static class UserBalance {
        final String userId;
        double balance;
        UserBalance(String userId, double balance) { this.userId = userId; this.balance = balance; }
    }
}
