package shop;

import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
public class Order {
    private final String orderId;
    private final List<CartItem> items;
    private final double finalAmount;
    private final String status;

    public Order(List<CartItem> items, double finalAmount) {
        this.orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        this.items = items;
        this.finalAmount = finalAmount;
        this.status = "PLACED";
    }
}
