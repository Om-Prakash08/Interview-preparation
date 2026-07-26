package shop;

import lombok.Getter;
import lombok.Synchronized;

public class CartItem {
    @Getter
    private final Product product;
    private int quantity;

    public CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    @Synchronized
    public int getQuantity() {
        return quantity;
    }

    @Synchronized
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getSubTotal() {
        return product.getPrice() * quantity;
    }
}
