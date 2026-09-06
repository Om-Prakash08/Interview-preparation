package vending;

public class VendingMachine {
    private final Inventory inventory;
    private final State idleState;
    private final State hasMoneyState;
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

    public Inventory getInventory()      { return inventory; }
    public State getIdleState()          { return idleState; }
    public State getHasMoneyState()      { return hasMoneyState; }
    public State getDispenseState()      { return dispenseState; }

    public synchronized void setState(State state)           { this.currentState = state; }
    public synchronized State getCurrentState()              { return currentState; }
    public synchronized double getDeposit()                  { return deposit; }
    public synchronized void addDeposit(double amount)       { this.deposit += amount; }
    public synchronized void clearDeposit()                  { this.deposit = 0.0; }
    public synchronized String getSelectedProductCode()      { return selectedProductCode; }
    public synchronized void setSelectedProductCode(String c){ this.selectedProductCode = c; }

    public synchronized void insertCoin(Coin coin)           { currentState.insertCoin(coin); }
    public synchronized void selectProduct(String code)      { currentState.selectProduct(code); }
    public synchronized void dispense()                      { currentState.dispenseProduct(); }
    public synchronized void cancelTransaction()             { currentState.refund(); }
}
