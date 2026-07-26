package vending;

public class HasMoneyState implements State {
    private final VendingMachine machine;

    public HasMoneyState(VendingMachine machine) {
        this.machine = machine;
    }

    @Override
    public void insertCoin(Coin coin) {
        machine.addDeposit(coin.getValue());
        System.out.printf("[HasMoney] Inserted coin: %s ($%.2f). Total deposit: $%.2f%n", 
                coin, coin.getValue(), machine.getDeposit());
    }

    @Override
    public void selectProduct(String code) {
        Inventory inventory = machine.getInventory();
        Product product = inventory.getProduct(code);

        if (product == null) {
            System.out.printf("[Selection] Invalid product code: %s%n", code);
            return;
        }

        if (!inventory.isAvailable(code)) {
            System.out.printf("[Selection] Product '%s' is out of stock.%n", product.getName());
            return;
        }

        double price = product.getPrice();
        double deposit = machine.getDeposit();

        if (deposit < price) {
            System.out.printf("[Selection] Insufficient balance for '%s'. Price: $%.2f, Deposit: $%.2f. Please insert more coins.%n", 
                    product.getName(), price, deposit);
            return;
        }

        System.out.printf("[Selection] Selected product: '%s' (Price: $%.2f)%n", product.getName(), price);
        machine.setSelectedProductCode(code);
        machine.setState(machine.getDispenseState());
        // Auto-dispense
        machine.dispense();
    }

    @Override
    public void dispenseProduct() {
        System.out.println("[HasMoney] Product must be selected first.");
    }

    @Override
    public void refund() {
        double deposit = machine.getDeposit();
        System.out.printf("[Cancel] Refunding total deposit: $%.2f%n", deposit);
        machine.clearDeposit();
        machine.setState(machine.getIdleState());
    }
}
