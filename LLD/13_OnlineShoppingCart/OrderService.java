package shop;

import lombok.Synchronized;
import java.util.List;

public class OrderService {
    private static OrderService instance;

    private OrderService() {}

    @Synchronized
    public static OrderService getInstance() {
        if (instance == null) {
            instance = new OrderService();
        }
        return instance;
    }

    // Thread-safe checkout process to prevent race conditions on stock decrement
    @Synchronized
    public Order placeOrder(ShoppingCart cart, CouponStrategy coupon, String userName) {
        List<CartItem> items = cart.getItems();
        if (items.isEmpty()) {
            System.out.printf("[Checkout Failed for %s] Cart is empty.%n", userName);
            return null;
        }

        // 1. Verify stock
        for (CartItem item : items) {
            Product product = item.getProduct();
            if (product.getStock() < item.getQuantity()) {
                System.out.printf("[Checkout Failed for %s] Insufficient stock for '%s'. Available: %d, Requested: %d%n",
                        userName, product.getName(), product.getStock(), item.getQuantity());
                return null;
            }
        }

        // 2. Decrement stock
        for (CartItem item : items) {
            item.getProduct().decrementStock(item.getQuantity());
        }

        // 3. Complete order
        double finalPrice = cart.calculateTotal(coupon);
        Order order = new Order(items, finalPrice);
        
        System.out.printf("[Checkout Success] Order %s created for %s. Paid: $%.2f%n",
                order.getOrderId(), userName, finalPrice);

        cart.clear();
        return order;
    }
}
