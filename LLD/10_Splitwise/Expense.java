package splitwise;

import java.util.List;

public class Expense {
    private final String id;
    private final User paidBy;
    private final double amount;
    private final SplitType splitType;
    private final List<Split> splits;

    public Expense(String id, User paidBy, double amount, SplitType splitType, List<Split> splits) {
        this.id = id;
        this.paidBy = paidBy;
        this.amount = amount;
        this.splitType = splitType;
        this.splits = splits;
    }

    public String getId()          { return id; }
    public User getPaidBy()        { return paidBy; }
    public double getAmount()      { return amount; }
    public SplitType getSplitType(){ return splitType; }
    public List<Split> getSplits() { return splits; }

    public boolean validate() {
        if (splitType == SplitType.EQUAL) return true;
        if (splitType == SplitType.EXACT) {
            double totalSum = 0;
            for (Split split : splits) totalSum += split.getAmount();
            return Math.abs(totalSum - amount) < 0.01;
        }
        if (splitType == SplitType.PERCENT) {
            double totalPercent = 0;
            for (Split split : splits) totalPercent += ((PercentSplit) split).getPercent();
            return Math.abs(totalPercent - 100.0) < 0.01;
        }
        return false;
    }
}
