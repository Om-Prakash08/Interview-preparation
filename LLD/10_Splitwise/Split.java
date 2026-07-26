package splitwise;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class Split {
    private final User user;
    protected double amount;

    public Split(User user) {
        this.user = user;
    }
}

class EqualSplit extends Split {
    public EqualSplit(User user) {
        super(user);
    }
}

class ExactSplit extends Split {
    public ExactSplit(User user, double amount) {
        super(user);
        this.amount = amount;
    }
}

class PercentSplit extends Split {
    @Getter
    private final double percent;

    public PercentSplit(User user, double percent) {
        super(user);
        this.percent = percent;
    }
}
