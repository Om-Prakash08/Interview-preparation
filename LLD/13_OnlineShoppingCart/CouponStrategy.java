package shop;

public interface CouponStrategy {
    double applyDiscount(double originalAmount);
}

class FlatDiscountCoupon implements CouponStrategy {
    private final double discountAmount;

    public FlatDiscountCoupon(double discountAmount) {
        this.discountAmount = discountAmount;
    }

    @Override
    public double applyDiscount(double originalAmount) {
        return Math.max(0.0, originalAmount - discountAmount);
    }
}

class PercentageDiscountCoupon implements CouponStrategy {
    private final double percentage;

    public PercentageDiscountCoupon(double percentage) {
        this.percentage = percentage;
    }

    @Override
    public double applyDiscount(double originalAmount) {
        return originalAmount * (1.0 - (percentage / 100.0));
    }
}
