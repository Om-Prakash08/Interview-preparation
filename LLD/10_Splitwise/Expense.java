package splitwise;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.List;

@Getter
@AllArgsConstructor
public class Expense {
    private final String id;
    private final User paidBy;
    private final double amount;
    private final SplitType splitType;
    private final List<Split> splits;

    public boolean validate() {
        if (splitType == SplitType.EQUAL) {
            return true;
        }
        if (splitType == SplitType.EXACT) {
            double totalSum = 0;
            for (Split split : splits) {
                totalSum += split.getAmount();
            }
            return Math.abs(totalSum - amount) < 0.01;
        }
        if (splitType == SplitType.PERCENT) {
            double totalPercent = 0;
            for (Split split : splits) {
                PercentSplit percentSplit = (PercentSplit) split;
                totalPercent += percentSplit.getPercent();
            }
            return Math.abs(totalPercent - 100.0) < 0.01;
        }
        return false;
    }
}
