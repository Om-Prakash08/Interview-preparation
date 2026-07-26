# 13. Online Shopping Cart (Java LLD Solution)

This folder contains a complete, thread-safe Java implementation of an Online Shopping Cart system.

Below is the **Complete Class Skeleton and API Design** so you can understand the entire system architecture, fields, and method signatures without looking at the source code.

---

## 1. Class Diagram / Architecture Skeleton

### Data Models
```java
public class Product {
    @Getter private final String id;
    @Getter private final String name;
    @Getter private final double price;
    private int stock;

    public Product(String id, String name, double price, int stock);
    @Synchronized public int getStock();
    @Synchronized public boolean decrementStock(int quantity); // Atomic check-and-decrement
    @Synchronized public void incrementStock(int quantity);
}

public class CartItem {
    @Getter private final Product product;
    private int quantity;

    @Synchronized public int getQuantity();
    @Synchronized public void setQuantity(int quantity);
    public double getSubTotal();
}
```

### Strategy Pattern (Coupons / Discounts)
```java
public interface CouponStrategy {
    double applyDiscount(double amount);
}

class FlatDiscountCoupon implements CouponStrategy {
    public FlatDiscountCoupon(double flatDiscount);
    @Override public double applyDiscount(double amount);
}

class PercentDiscountCoupon implements CouponStrategy {
    public PercentDiscountCoupon(double percentDiscount); // e.g. 10.0 for 10%
    @Override public double applyDiscount(double amount);
}
```

### Cart Context & Order Records
```java
public class ShoppingCart {
    @Synchronized public void addItem(Product product, int quantity);
    @Synchronized public void removeItem(String productId);
    @Synchronized public void updateQuantity(String productId, int quantity);
    @Synchronized public List<CartItem> getItems(); // Returns copy
    @Synchronized public double calculateTotal(CouponStrategy coupon);
    @Synchronized public void clear();
}

@Getter
public class Order {
    private final String orderId;
    private final List<CartItem> items;
    private final double finalAmount;
    private final String status;
}

public class OrderService {
    @Synchronized public static OrderService getInstance();
    @Synchronized public Order placeOrder(ShoppingCart cart, CouponStrategy coupon, String userName); // Atomic checkout
}
```

---

## 2. Core Workflow & Usage

Here is how shopping cart modifications and atomic orders are processed:

```java
OrderService orderService = OrderService.getInstance();

// 1. Setup products
Product macbook = new Product("p1", "MacBook Pro", 1000.0, 2); // Stock = 2
ShoppingCart cart = new ShoppingCart();

// 2. Modify cart
cart.addItem(macbook, 1);

// 3. Checkout with 10% discount coupon
CouponStrategy discount = new PercentDiscountCoupon(10.0);
Order order = orderService.placeOrder(cart, discount, "Bob"); // Deducts 1 stock from MacBook Pro
```

---

## 3. Concurrency & Thread-Safety Details
- **Atomic Checkout Verification**: The `placeOrder` method in `OrderService` is synchronized (`@Synchronized`), protecting stock allocation checks.
- **Stock Double-Selling Prevention**: The `decrementStock` method inside `Product` checks if `quantity <= stock` and deducts the inventory in a single synchronized block. This prevents race conditions where two concurrent customer checkout threads verify stock availability and sell the same remaining item.
- **Cart Operation Safety**: Modifying shopping cart items is synchronized (`@Synchronized`) at the cart instance level to support safe concurrent updates.
