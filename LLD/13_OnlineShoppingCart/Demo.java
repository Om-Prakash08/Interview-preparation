package shop;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FAANG System Design Demo: Online Shopping Cart ===");

        OrderService orderService = OrderService.getInstance();

        // 1. Initialize products
        Product p1 = new Product("prod-1", "MacBook Pro", 1000.0, 2);
        Product p2 = new Product("prod-2", "Sony WH-1000XM4", 300.0, 5);

        System.out.printf("Products registered: MacBook Pro ($1000.00, stock: 2), Sony Headphones ($300.00, stock: 5).%n");

        // 2. Bob checkout with 10% coupon
        System.out.println("\n--- Bob's Shopping Flow (Applying 10% percentage discount coupon) ---");
        ShoppingCart bobCart = new ShoppingCart();
        bobCart.addItem(p1, 1); 
        bobCart.addItem(p2, 2); 
        
        CouponStrategy tenPercentCoupon = new PercentageDiscountCoupon(10.0);
        System.out.printf("Bob's cart total before discount: $%.2f%n", bobCart.calculateTotal(null));
        System.out.printf("Bob's cart total after 10%% discount: $%.2f%n", bobCart.calculateTotal(tenPercentCoupon));

        orderService.placeOrder(bobCart, tenPercentCoupon, "Bob");

        System.out.printf("Stock after Bob's order: MacBook: %d, Headphones: %d%n", p1.getStock(), p2.getStock());

        // 3. Concurrency check: Charlie and David try to buy the last MacBook Pro (stock = 1)
        System.out.println("\n--- Concurrency check: Charlie and David try to buy the last remaining MacBook Pro simultaneously ---");
        
        ShoppingCart charlieCart = new ShoppingCart();
        charlieCart.addItem(p1, 1); 

        ShoppingCart davidCart = new ShoppingCart();
        davidCart.addItem(p1, 1); 

        ExecutorService executor = Executors.newFixedThreadPool(2);
        executor.submit(() -> orderService.placeOrder(charlieCart, null, "Charlie"));
        executor.submit(() -> orderService.placeOrder(davidCart, null, "David"));

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.printf("Final stock of MacBook Pro: %d%n", p1.getStock());
        System.out.println("\n=== Shopping Cart Demo Finished successfully ===");
    }
}
