# 09. Logger Framework (Java LLD Solution)

This folder contains a complete, asynchronous Java implementation of a Logger Framework.

Below is the **Complete Class Skeleton and API Design** so you can understand the entire system architecture, fields, and method signatures without looking at the source code.

---

## 1. Class Diagram / Architecture Skeleton

### Enums & Messages
```java
public enum LogLevel { DEBUG, INFO, ERROR }

@Getter
public class LogMessage {
    private final LocalDateTime timestamp;
    private final LogLevel level;
    private final String message;
    private final String threadName;

    public LogMessage(LogLevel level, String message);
    @Override public String toString(); // Custom formatting
}
```

### Observer Pattern (Appenders / Sinks)
```java
public interface LogSink {
    void log(LogMessage message);
}

public class ConsoleSink implements LogSink {
    @Override public void log(LogMessage message); // Writes to System.out
}

public class FileSink implements LogSink {
    public FileSink(String filePath);
    @Override public void log(LogMessage message); // Writes to log file
}
```

### Logger Engine (Singleton / Asynchronous Queue)
```java
public class Logger {
    private static Logger instance;
    private LogLevel thresholdLevel = LogLevel.INFO;
    private final List<LogSink> sinks = new ArrayList<>();
    private final BlockingQueue<LogMessage> logQueue = new LinkedBlockingQueue<>();
    private final Thread workerThread;
    private volatile boolean running = true;

    @Synchronized public static Logger getInstance();
    @Synchronized public void setThreshold(LogLevel level);
    @Synchronized public void addSink(LogSink sink);

    public void log(LogLevel level, String message); // Enqueues logs asynchronously if severity matches threshold
    public void info(String message);
    public void debug(String message);
    public void error(String message);

    private void drainQueue(); // Run loop for background workerThread
    @Synchronized private void dispatch(LogMessage message); // Notifies sinks
    public void shutdown();
}
```

---

## 2. Core Workflow & Usage

Here is how the logging pipeline is initialized and run asynchronously:

```java
Logger logger = Logger.getInstance();

// 1. Configure Threshold and Appenders (Sinks)
logger.setThreshold(LogLevel.DEBUG);
logger.addSink(new ConsoleSink());
logger.addSink(new FileSink("log.txt"));

// 2. Publish Log Messages (Non-blocking call)
logger.info("Application starting up."); // Instantly enqueued, main thread continues
logger.debug("Database Connection established.");

// 3. Graceful shutdown
logger.shutdown();
```

---

## 3. Concurrency & Thread-Safety Details
- **Non-blocking Log Submission**: Application threads do not block when calling `logger.log()`. Logs are offered to a thread-safe `LinkedBlockingQueue` instantly.
- **Dedicated Daemon Thread**: A background thread (`Logger-Worker-Thread`) continuously drains the queue and dispatches the formatting/I/O task to the target sinks, keeping application flows highly performant.
- **Observer List Protection**: Adding appenders and dispatching logs are fully synchronized (`@Synchronized`) to avoid list iteration issues under concurrent changes.
