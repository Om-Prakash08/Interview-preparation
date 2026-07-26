package vending;

public class IdleState implements State {
    private final VendingMachine machine;

    public IdleState(VendingMachine machine) {
        this.machine = machine;
    }

    @Override
    public void insertCoin(Coin coin) {
        machine.addDeposit(coin.getValue());
        System.out.printf("[Idle] Inserted coin: %s ($%.2f). Total deposit: $%.2f%n", 
                coin, coin.getValue(), machine.getDeposit());
        machine.setState(machine.getHasMoneyState());
    }

    @Override
    public void selectProduct(String code) {
        System.out.println("[Idle] Please insert coins first before making a selection.");
    }

    @Override
    public void dispenseProduct() {
        System.out.println("[Idle] Payment required before dispensing.");
    }

    @Override
    public void refund() {
        System.out.println("[Idle] No deposit to refund.");
    }
}
