package splitwise;

public abstract class Split {
    private final User user;
    protected double amount;

    public Split(User user) { this.user = user; }

    public User getUser()       { return user; }
    public double getAmount()   { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
}

class EqualSplit extends Split {
    public EqualSplit(User user) { super(user); }
}

class ExactSplit extends Split {
    public ExactSplit(User user, double amount) {
        super(user);
        this.amount = amount;
    }
}

class PercentSplit extends Split {
    private final double percent;

    public PercentSplit(User user, double percent) {
        super(user);
        this.percent = percent;
    }

    public double getPercent() { return percent; }
}
