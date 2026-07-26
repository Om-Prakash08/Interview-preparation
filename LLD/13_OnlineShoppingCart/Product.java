package shop;

import lombok.Getter;
import lombok.Synchronized;

public class Product {
    @Getter
    private final String id;
    @Getter
    private final String name;
    @Getter
    private final double price;
    private int stock;

    public Product(String id, String name, double price, int stock) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }

    @Synchronized
    public int getStock() {
        return stock;
    }

    @Synchronized
    public boolean decrementStock(int quantity) {
        if (quantity <= stock) {
            stock -= quantity;
            return true;
        }
        return false;
    }

    @Synchronized
    public void incrementStock(int quantity) {
        stock += quantity;
    }
}
