package atm;

public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FAANG System Design Demo: ATM Machine ===");

        // 1. Setup bank databases
        BankService bankService = new BankService();
        Account acc1 = new Account("123456789", "John Doe", 2000.0);
        Card card1 = new Card("CARD-9876", 1234, "123456789");

        bankService.addAccount(acc1);
        bankService.addCard(card1);

        // 2. Setup CashDispenser (5x $100, 10x $50, 20x $20 -> $1400 total)
        CashDispenser cashDispenser = new CashDispenser(5, 10, 20);
        ATM atm = new ATM(bankService, cashDispenser);

        System.out.printf("ATM initialized. Cash in dispenser: $%.2f%n", cashDispenser.getTotalAvailableCash());

        // Scenario 1: Successful check balance and withdrawal of $270
        System.out.println("\n--- Scenario 1: Successful check balance & withdrawal of $270 ---");
        atm.insertCard(card1);
        atm.enterPin(1234);
        atm.checkBalance();
        atm.withdraw(270);
        atm.checkBalance();
        atm.ejectCard();

        // Scenario 2: Incorrect PIN
        System.out.println("\n--- Scenario 2: Attempting entry with incorrect PIN ---");
        atm.insertCard(card1);
        atm.enterPin(9999); 

        // Scenario 3: Attempting to withdraw more than account balance
        System.out.println("\n--- Scenario 3: Attempting withdrawal exceeding account balance ---");
        atm.insertCard(card1);
        atm.enterPin(1234);
        atm.withdraw(3000); 
        atm.ejectCard();

        // Scenario 4: Attempting withdrawal that is not a multiple of bills ($25)
        System.out.println("\n--- Scenario 4: Attempting withdrawal of unsupported amount ($25) ---");
        atm.insertCard(card1);
        atm.enterPin(1234);
        atm.withdraw(25); 
        atm.ejectCard();

        System.out.println("\n=== ATM Demo Finished successfully ===");
    }
}
