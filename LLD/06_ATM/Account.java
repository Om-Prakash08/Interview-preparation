package atm;

public class Account {
    private final String accountNumber;
    private double balance;
    private final String ownerName;

    public Account(String accountNumber, String ownerName, double initialBalance) {
        this.accountNumber = accountNumber;
        this.ownerName = ownerName;
        this.balance = initialBalance;
    }

    public String getAccountNumber() { return accountNumber; }
    public String getOwnerName()     { return ownerName; }

    public synchronized double getBalance()          { return balance; }
    public synchronized void debit(double amount)    { if (amount <= balance) balance -= amount; }
    public synchronized void credit(double amount)   { balance += amount; }
}
