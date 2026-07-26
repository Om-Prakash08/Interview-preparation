package shop;

import lombok.Synchronized;
import java.util.*;

public class ShoppingCart {
    private final Map<String, CartItem> items = new HashMap<>();

    @Synchronized
    public void addItem(Product product, int quantity) {
        if (product == null || quantity <= 0) return;
        
        CartItem item = items.get(product.getId());
        if (item != null) {
            item.setQuantity(item.getQuantity() + quantity);
        } else {
            items.put(product.getId(), new CartItem(product, quantity));
        }
        System.out.printf("[Cart] Added %d x '%s' to cart.%n", quantity, product.getName());
    }

    @Synchronized
    public void removeItem(String productId) {
        CartItem removed = items.remove(productId);
        if (removed != null) {
            System.out.printf("[Cart] Removed '%s' from cart.%n", removed.getProduct().getName());
        }
    }

    @Synchronized
    public void updateQuantity(String productId, int quantity) {
        CartItem item = items.get(productId);
        if (item != null) {
            if (quantity <= 0) {
                items.remove(productId);
            } else {
                item.setQuantity(quantity);
            }
            System.out.printf("[Cart] Updated quantity of '%s' to %d.%n", item.getProduct().getName(), quantity);
        }
    }

    @Synchronized
    public List<CartItem> getItems() {
        return new ArrayList<>(items.values());
    }

    @Synchronized
    public double calculateTotal(CouponStrategy coupon) {
        double sum = 0.0;
        for (CartItem item : items.values()) {
            sum += item.getSubTotal();
        }
        if (coupon != null) {
            return coupon.applyDiscount(sum);
        }
        return sum;
    }

    @Synchronized
    public void clear() {
        items.clear();
    }
}
