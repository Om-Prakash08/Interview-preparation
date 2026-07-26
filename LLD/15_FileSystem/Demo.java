package fs;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Demo {
    public static void main(String[] args) {
        System.out.println("=== FAANG System Design Demo: In-Memory File System ===");

        FileSystem fs = FileSystem.getInstance();

        // 1. mkdir and add contents
        System.out.println("\n--- Creating directory structure ---");
        fs.mkdir("/a/b/c");

        System.out.println("\n--- Adding file /a/b/c/d.txt with 'Hello World' ---");
        fs.addContentToFile("/a/b/c/d.txt", "Hello World");

        System.out.println("\n--- Reading file contents ---");
        String content = fs.readContentFromFile("/a/b/c/d.txt");
        System.out.printf("Content of /a/b/c/d.txt: '%s'%n", content);

        System.out.println("\n--- Appending more content to /a/b/c/d.txt ---");
        fs.addContentToFile("/a/b/c/d.txt", " - Added more text");
        System.out.printf("New content of /a/b/c/d.txt: '%s'%n", fs.readContentFromFile("/a/b/c/d.txt"));

        // 2. Sizing Verification (Composite pattern test)
        System.out.println("\n--- Verification of Composite sizing recursively ---");
        System.out.printf("Size of file /a/b/c/d.txt: %d bytes%n", fs.getSize("/a/b/c/d.txt"));
        System.out.printf("Size of Directory /a/b/c: %d bytes%n", fs.getSize("/a/b/c"));
        System.out.printf("Size of Directory /a: %d bytes%n", fs.getSize("/a"));

        // 3. ls directories
        System.out.println("\n--- ls directories ---");
        System.out.printf("ls /: %s%n", fs.ls("/"));
        System.out.printf("ls /a/b/c: %s%n", fs.ls("/a/b/c"));

        // 4. Concurrency check (simultaneous mkdir)
        System.out.println("\n--- Concurrency check: Simultaneous directory creation ---");
        ExecutorService executor = Executors.newFixedThreadPool(4);

        for (int i = 1; i <= 5; i++) {
            final int id = i;
            executor.submit(() -> fs.mkdir("/a/b/concurrent_dir_" + id));
        }

        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.printf("\nUpdated ls /a/b: %s%n", fs.ls("/a/b"));

        System.out.println("\n=== File System Demo Finished successfully ===");
    }
}
