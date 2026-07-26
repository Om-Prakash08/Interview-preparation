package vending;

public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FAANG System Design Demo: Vending Machine ===");

        VendingMachine machine = new VendingMachine();

        // 1. Initialize Inventory
        Product coke = new Product("Coke", 1.50);
        Product pepsi = new Product("Pepsi", 1.25);
        Product water = new Product("Water", 1.00);

        machine.getInventory().addProduct("A1", coke, 5);
        machine.getInventory().addProduct("A2", pepsi, 2);
        machine.getInventory().addProduct("A3", water, 0); // Out of stock

        System.out.println("Vending machine initialized with Coke ($1.50, stock: 5), Pepsi ($1.25, stock: 2), and Water ($1.00, stock: 0).");

        // Scenario 1: Normal Purchase of Coke ($1.50)
        System.out.println("\n--- Scenario 1: Successful Purchase of Coke ($1.50) ---");
        machine.insertCoin(Coin.DOLLAR);
        machine.insertCoin(Coin.QUARTER);
        machine.insertCoin(Coin.QUARTER); // Deposit = $1.50
        machine.selectProduct("A1"); // Coke

        // Scenario 2: Purchase of Pepsi ($1.25) with Change
        System.out.println("\n--- Scenario 2: Purchase of Pepsi ($1.25) with Change ---");
        machine.insertCoin(Coin.DOLLAR);
        machine.insertCoin(Coin.DOLLAR); // Deposit = $2.00
        machine.selectProduct("A2"); // Pepsi (Change should be $0.75)

        // Scenario 3: Out of Stock Selection
        System.out.println("\n--- Scenario 3: Attempting to purchase Water (Out of Stock) ---");
        machine.insertCoin(Coin.DOLLAR);
        machine.selectProduct("A3"); // Water (Out of stock)
        machine.cancelTransaction(); // Should refund $1.00

        // Scenario 4: Insufficient Funds
        System.out.println("\n--- Scenario 4: Attempting purchase with insufficient funds ---");
        machine.insertCoin(Coin.QUARTER);
        machine.selectProduct("A1"); // Coke ($1.50) - Should prompt to insert more coins
        machine.insertCoin(Coin.DOLLAR);
        machine.insertCoin(Coin.QUARTER); // Deposit = $1.50 now, select again
        machine.selectProduct("A1"); // Coke - succeeds

        System.out.println("\n=== Demo Finished successfully ===");
    }
}
