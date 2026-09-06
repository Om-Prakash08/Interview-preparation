package shop;

import java.util.List;
import java.util.UUID;

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

    public String getOrderId()        { return orderId; }
    public List<CartItem> getItems()  { return items; }
    public double getFinalAmount()    { return finalAmount; }
    public String getStatus()         { return status; }
}
