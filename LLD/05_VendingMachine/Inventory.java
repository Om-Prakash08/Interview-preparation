package vending;

import java.util.HashMap;
import java.util.Map;

public class Inventory {
    private final Map<String, Product> products;
    private final Map<String, Integer> quantities;

    public Inventory() {
        this.products = new HashMap<>();
        this.quantities = new HashMap<>();
    }

    public synchronized void addProduct(String code, Product product, int quantity) {
        products.put(code, product);
        quantities.put(code, quantity);
    }

    public synchronized Product getProduct(String code) {
        return products.get(code);
    }

    public synchronized boolean isAvailable(String code) {
        return products.containsKey(code) && quantities.get(code) > 0;
    }

    public synchronized void decrementQuantity(String code) {
        if (quantities.containsKey(code)) {
            int current = quantities.get(code);
            if (current > 0) {
                quantities.put(code, current - 1);
            }
        }
    }

    public synchronized int getQuantity(String code) {
        return quantities.getOrDefault(code, 0);
    }
}
