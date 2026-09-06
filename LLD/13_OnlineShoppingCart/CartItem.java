package shop;

public class CartItem {
    private final Product product;
    private int quantity;

    public CartItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public Product getProduct()           { return product; }
    public synchronized int getQuantity() { return quantity; }
    public synchronized void setQuantity(int quantity) { this.quantity = quantity; }

    public double getSubTotal() { return product.getPrice() * quantity; }
}
