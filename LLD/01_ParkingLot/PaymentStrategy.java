package parkinglot;

public interface PaymentStrategy {
    boolean processPayment(double amount);
}

class CashPaymentStrategy implements PaymentStrategy {
    @Override
    public boolean processPayment(double amount) {
        System.out.printf("Processing cash payment of $%.2f. Success!%n", amount);
        return true;
    }
}

class CreditCardPaymentStrategy implements PaymentStrategy {
    private final String cardNumber;
    private final String name;

    public CreditCardPaymentStrategy(String cardNumber, String name) {
        this.cardNumber = cardNumber;
        this.name = name;
    }

    @Override
    public boolean processPayment(double amount) {
        System.out.printf("Processing credit card payment of $%.2f using card ending in %s. Success!%n", 
                amount, cardNumber.substring(Math.max(0, cardNumber.length() - 4)));
        return true;
    }
}

class MobileWalletPaymentStrategy implements PaymentStrategy {
    private final String phoneNumber;

    public MobileWalletPaymentStrategy(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    @Override
    public boolean processPayment(double amount) {
        System.out.printf("Processing mobile wallet payment of $%.2f for number %s. Success!%n", amount, phoneNumber);
        return true;
    }
}
