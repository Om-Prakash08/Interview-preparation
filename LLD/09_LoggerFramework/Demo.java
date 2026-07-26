package logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FAANG System Design Demo: Logger Framework ===");

        Logger logger = Logger.getInstance();
        logger.addSink(new ConsoleSink());
        logger.addSink(new FileSink());

        // 1. Verify threshold filtering (INFO)
        logger.setThreshold(LogLevel.INFO);
        System.out.println("Threshold level set to INFO. (Logs below INFO like DEBUG will be ignored)");

        logger.info("Application starting up.");
        logger.debug("Establishing connection to database (should not appear).");
        logger.error("Failed to fetch config record.");

        // Wait for background worker processing
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}

        // 2. Verify threshold change (DEBUG)
        System.out.println("\nThreshold level changed to DEBUG.");
        logger.setThreshold(LogLevel.DEBUG);
        logger.debug("Database Connection established successfully (should now appear).");

        try { Thread.sleep(200); } catch (InterruptedException ignored) {}

        // 3. Concurrent Logging Test
        System.out.println("\n--- Concurrency check: Multiple worker threads logging concurrently ---");
        ExecutorService executor = Executors.newFixedThreadPool(4);

        for (int i = 1; i <= 5; i++) {
            final int threadId = i;
            executor.submit(() -> {
                logger.info("Service request processed by concurrent worker " + threadId);
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Wait to finish printing logs
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        
        logger.shutdown();
        System.out.println("\n=== Logger Demo Finished successfully ===");
    }
}
