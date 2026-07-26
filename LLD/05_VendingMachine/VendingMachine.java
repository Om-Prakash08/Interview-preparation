package vending;

import lombok.Getter;
import lombok.Synchronized;

public class VendingMachine {
    @Getter
    private final Inventory inventory;
    @Getter
    private final State idleState;
    @Getter
    private final State hasMoneyState;
    @Getter
    private final State dispenseState;

    private State currentState;
    private double deposit = 0.0;
    private String selectedProductCode = null;

    public VendingMachine() {
        this.inventory = new Inventory();
        this.idleState = new IdleState(this);
        this.hasMoneyState = new HasMoneyState(this);
        this.dispenseState = new DispenseState(this);
        this.currentState = idleState;
    }

    @Synchronized
    public void setState(State state) {
        this.currentState = state;
    }

    @Synchronized
    public State getCurrentState() {
        return currentState;
    }

    @Synchronized
    public double getDeposit() {
        return deposit;
    }

    @Synchronized
    public void addDeposit(double amount) {
        this.deposit += amount;
    }

    @Synchronized
    public void clearDeposit() {
        this.deposit = 0.0;
    }

    @Synchronized
    public String getSelectedProductCode() {
        return selectedProductCode;
    }

    @Synchronized
    public void setSelectedProductCode(String code) {
        this.selectedProductCode = code;
    }

    @Synchronized
    public void insertCoin(Coin coin) {
        currentState.insertCoin(coin);
    }

    @Synchronized
    public void selectProduct(String code) {
        currentState.selectProduct(code);
    }

    @Synchronized
    public void dispense() {
        currentState.dispenseProduct();
    }

    @Synchronized
    public void cancelTransaction() {
        currentState.refund();
    }
}
