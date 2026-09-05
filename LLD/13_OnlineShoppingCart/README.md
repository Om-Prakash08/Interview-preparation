# 13. Online Shopping Cart — LLD Interview Guide

> **Framework:** Requirements → Entities → Class Design → Implementation → Extensibility

---

## ① Requirements (Clarify First!)

> *"Before I write any code, let me confirm the scope."*

**Functional Requirements:**
- Browse products (name, price, stock quantity)
- Add, remove, and update quantity of items in the **shopping cart**
- Apply a **discount coupon** at checkout (flat or percentage)
- Place an **order**: atomically deducts stock and confirms purchase
- Cart can be cleared after order is placed

**Non-Functional Requirements:**
- **Thread-safe**: Multiple customers checking out the same low-stock product concurrently
- **Singleton** order service — centralized order placement
- Prevent **overselling** (selling more units than available stock)

**Out of Scope:**
- Payment gateway integration
- Order tracking / delivery
- Wishlist / saved items

---

## ② Entities (Nouns → Classes)

> *"I'll identify the key nouns to define my classes."*

| Entity | Type | Responsibility |
|---|---|---|
| `Product` | Class | id, name, price, stock; atomic decrementStock |
| `CartItem` | Class | product reference + quantity + subTotal |
| `ShoppingCart` | Class | Map of productId → CartItem; CRUD operations |
| `CouponStrategy` | Interface | `applyDiscount(amount)` |
| `FlatDiscountCoupon` | Concrete | `amount - flatDiscount` |
| `PercentDiscountCoupon` | Concrete | `amount × (1 - percent/100)` |
| `Order` | Class | orderId, items snapshot, finalAmount, status |
| `OrderService` | Singleton | `placeOrder()` — atomic checkout |

---

## ③ Class Design (Design Patterns)

> *"I'll highlight the design patterns used and why."*

### 🔷 Strategy Pattern — Discount Coupons
```
CouponStrategy (interface)
    ├── FlatDiscountCoupon(50.0)    → deducts $50 flat
    └── PercentDiscountCoupon(10.0) → deducts 10%
```
**Why?** New coupon types (BOGO, loyalty points, seasonal) can be added without touching `ShoppingCart` or `OrderService`.

### 🔷 Atomic Stock Decrement — Key Race Condition Defense
```java
// Product.decrementStock() — must be atomic:
@Synchronized
public boolean decrementStock(int qty) {
    if (stock < qty) return false; // Insufficient stock
    stock -= qty;                  // Deduct atomically
    return true;
}
```
**Why?** Without synchronized block, two threads could both read `stock=1`, both pass the check, and both deduct → stock becomes -1 (oversold).

### 🔷 Class Skeleton
```java
public class Product {
    private final String id, name;
    private final double price;
    private int stock;

    @Synchronized public int getStock();
    @Synchronized public boolean decrementStock(int qty); // Atomic check-and-deduct
    @Synchronized public void incrementStock(int qty);
}

public class CartItem {
    private final Product product;
    private int quantity;

    @Synchronized public int getQuantity();
    @Synchronized public void setQuantity(int qty);
    public double getSubTotal(); // product.price × quantity
}

public interface CouponStrategy {
    double applyDiscount(double amount);
}

public class ShoppingCart {
    @Synchronized public void addItem(Product product, int qty);
    @Synchronized public void removeItem(String productId);
    @Synchronized public void updateQuantity(String productId, int qty);
    @Synchronized public List<CartItem> getItems();
    @Synchronized public double calculateTotal(CouponStrategy coupon);
    @Synchronized public void clear();
}

public class Order {
    private final String orderId;
    private final List<CartItem> items;
    private final double finalAmount;
    private final String status; // PLACED, FAILED
}

public class OrderService {
    @Synchronized public static OrderService getInstance();
    @Synchronized public Order placeOrder(ShoppingCart cart, CouponStrategy coupon, String userName);
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Setup & Cart Operations
```java
OrderService orderService = OrderService.getInstance();

Product macbook = new Product("p1", "MacBook Pro", 1000.0, 2); // Stock = 2
Product iphone  = new Product("p2", "iPhone 15",   800.0,  5);

ShoppingCart cart = new ShoppingCart();

// 1. Build Cart
cart.addItem(macbook, 1);  // 1× MacBook Pro = $1000
cart.addItem(iphone, 2);   // 2× iPhone 15   = $1600
// Total before discount = $2600

// 2. Update quantity
cart.updateQuantity("p2", 1); // Change iPhone qty to 1 → $800
// Total = $1800

// 3. Remove item
cart.removeItem("p2"); // Remove iPhone entirely
// Total = $1000
```

### Checkout Flow (inside `placeOrder`)
```java
CouponStrategy coupon = new PercentDiscountCoupon(10.0); // 10% off

Order order = orderService.placeOrder(cart, coupon, "Bob");
// Internal steps (synchronized):
// 1. getItems() snapshot from cart
// 2. For each CartItem:
//    a. product.decrementStock(quantity) → if false → throw "Out of Stock"
// 3. totalAmount = cart.calculateTotal(coupon) = $1000 × 0.90 = $900
// 4. Create Order(PLACED, items, $900)
// 5. cart.clear()
// 6. Return Order
```

### Concurrent Scenario (Race Condition Prevention)
```
Thread A (Bob):   placeOrder(cart with 1× MacBook) → decrementStock(1) → stock: 2→1 ✓
Thread B (Alice): placeOrder(cart with 2× MacBook) → decrementStock(2) → stock: 1 < 2 → FAIL ✗
Alice gets "Insufficient stock" exception
```

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| BOGO coupon | `class BogoCoupon implements CouponStrategy` |
| Payment integration | Add `PaymentService.charge(amount)` call inside `placeOrder()` |
| Wishlist | Add separate `WishlistService` with `Set<Product>` |
| Order history | Store `List<Order>` per user in `OrderService` |
| Inventory alerts | `Product.decrementStock()` triggers event if `stock < threshold` |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `OrderService.placeOrder()` | `@Synchronized` | Atomic multi-item checkout with stock validation |
| `Product.decrementStock()` | `@Synchronized` | Prevent race between stock-check and stock-deduct |
| `ShoppingCart.addItem()` | `@Synchronized` | Safe concurrent cart modifications |
| `ShoppingCart.calculateTotal()` | `@Synchronized` | Consistent total during concurrent cart updates |
