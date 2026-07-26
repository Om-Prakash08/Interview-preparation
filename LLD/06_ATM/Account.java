package atm;

import lombok.Getter;
import lombok.Synchronized;

public class Account {
    @Getter
    private final String accountNumber;
    private double balance;
    @Getter
    private final String ownerName;

    public Account(String accountNumber, String ownerName, double initialBalance) {
        this.accountNumber = accountNumber;
        this.ownerName = ownerName;
        this.balance = initialBalance;
    }

    @Synchronized
    public double getBalance() {
        return balance;
    }

    @Synchronized
    public void debit(double amount) {
        if (amount <= balance) {
            balance -= amount;
        }
    }

    @Synchronized
    public void credit(double amount) {
        balance += amount;
    }
}
