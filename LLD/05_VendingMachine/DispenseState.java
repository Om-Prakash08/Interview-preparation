package vending;

public class DispenseState implements State {
    private final VendingMachine machine;

    public DispenseState(VendingMachine machine) {
        this.machine = machine;
    }

    @Override
    public void insertCoin(Coin coin) {
        System.out.println("[Dispense] Cannot accept coins while dispensing.");
    }

    @Override
    public void selectProduct(String code) {
        System.out.println("[Dispense] Cannot alter selection while dispensing.");
    }

    @Override
    public void dispenseProduct() {
        String code = machine.getSelectedProductCode();
        Inventory inventory = machine.getInventory();
        Product product = inventory.getProduct(code);

        // Decrement stock
        inventory.decrementQuantity(code);
        
        // Return change
        double change = machine.getDeposit() - product.getPrice();
        
        System.out.printf("[Dispense] Dispensing: '%s' ($%.2f)%n", product.getName(), product.getPrice());
        if (change > 0.001) {
            System.out.printf("[Dispense] Dispensed change: $%.2f%n", change);
        }

        // Reset transaction
        machine.clearDeposit();
        machine.setSelectedProductCode(null);
        machine.setState(machine.getIdleState());
    }

    @Override
    public void refund() {
        System.out.println("[Dispense] Cannot refund once dispensing process starts.");
    }
}
