package cache;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FAANG System Design Demo: LRU Cache ===");

        // 1. Core LRU Cache operations
        LRUCache<Integer, String> cache = new LRUCache<>(3);
        System.out.println("Created LRU Cache of capacity 3.");

        System.out.println("\n--- Inserting keys 1, 2, 3 ---");
        cache.put(1, "Value_A");
        cache.put(2, "Value_B");
        cache.put(3, "Value_C");

        System.out.printf("Get key 1: %s (Key 1 becomes Most Recently Used)%n", cache.get(1));

        System.out.println("\n--- Inserting key 4 (Should trigger eviction of Key 2) ---");
        cache.put(4, "Value_D"); // Eviction occurs here

        System.out.printf("Get key 2 (evicted): %s%n", cache.get(2)); // Should be null
        System.out.printf("Get key 1 (retained): %s%n", cache.get(1)); // Should be "Value_A"
        System.out.printf("Get key 3 (retained): %s%n", cache.get(3)); // Should be "Value_C"
        System.out.printf("Get key 4 (retained): %s%n", cache.get(4)); // Should be "Value_D"

        // 2. Concurrency Test
        System.out.println("\n--- Concurrency check: Multiple threads reading/writing ---");
        LRUCache<Integer, Integer> concurrentCache = new LRUCache<>(10);
        ExecutorService executor = Executors.newFixedThreadPool(5);

        for (int i = 1; i <= 50; i++) {
            final int value = i;
            executor.submit(() -> {
                concurrentCache.put(value % 15, value);
                concurrentCache.get(value % 15);
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.printf("Completed concurrent execution. Final Cache Size: %d%n", concurrentCache.size());
        System.out.println("\n=== Cache Demo Finished successfully ===");
    }
}
