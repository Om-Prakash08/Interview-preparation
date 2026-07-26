package vending;

public interface State {
    void insertCoin(Coin coin);
    void selectProduct(String code);
    void dispenseProduct();
    void refund();
}
