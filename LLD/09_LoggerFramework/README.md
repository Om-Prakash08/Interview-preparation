# 09. Logger Framework — LLD Interview Guide

> **Framework:** Requirements → Entities → Class Design → Implementation → Extensibility

---

## ① Requirements (Clarify First!)

> *"Before I write any code, let me confirm the scope."*

**Functional Requirements:**
- Log messages at different severity levels: `DEBUG`, `INFO`, `ERROR`
- Configurable **threshold**: messages below the threshold are silently dropped
- Support multiple **output destinations (sinks)**: Console, File (extensible)
- Each log message includes: timestamp, level, thread name, message body
- Calling `logger.log()` must **not block** the application thread

**Non-Functional Requirements:**
- **Asynchronous**: Log writes happen in a background thread; application threads return immediately
- **Thread-safe**: Multiple threads can call `logger.log()` concurrently
- **Singleton** Logger — global point of access

**Out of Scope:**
- Log rotation / archival
- Remote log aggregation (e.g., Splunk, Datadog)
- Structured/JSON logging (can extend)

---

## ② Entities (Nouns → Classes)

> *"I'll identify the key nouns to define my classes."*

| Entity | Type | Responsibility |
|---|---|---|
| `LogLevel` | Enum | `DEBUG, INFO, ERROR` (severity ordering) |
| `LogMessage` | Class | Immutable log record: timestamp, level, thread, message |
| `LogSink` | Interface | Output contract: `log(LogMessage)` |
| `ConsoleSink` | Concrete | Writes to `System.out` |
| `FileSink` | Concrete | Appends to a log file |
| `Logger` | Singleton | Threshold filter, sink registry, async queue, worker thread |

---

## ③ Class Design (Design Patterns)

> *"I'll highlight the design patterns used and why."*

### 🔷 Observer Pattern — Sinks as Listeners
```
Logger (Subject)
    └── List<LogSink> sinks (Observers)
        ├── ConsoleSink → prints to stdout
        └── FileSink    → appends to file
```
**Why?** Adding a new output destination (e.g., `SlackSink`, `DatabaseSink`) requires only implementing `LogSink` and calling `logger.addSink(new SlackSink(...))`. Zero changes to the core logger.

### 🔷 Producer-Consumer — Async Queue
```
Application Threads (Producers) → BlockingQueue<LogMessage> → Logger-Worker-Thread (Consumer)
```
**Why?** Decouples log production from I/O. App threads never wait for disk or console I/O.

### 🔷 Singleton — Logger
```java
@Synchronized public static Logger getInstance();
```

### 🔷 Class Skeleton
```java
public enum LogLevel { DEBUG, INFO, ERROR }

public class LogMessage {
    private final LocalDateTime timestamp;
    private final LogLevel level;
    private final String message;
    private final String threadName;

    public LogMessage(LogLevel level, String message);
    @Override public String toString(); // "[2024-01-01 10:00:00] [INFO] [main] Application started"
}

public interface LogSink {
    void log(LogMessage message);
}

public class ConsoleSink implements LogSink { ... }
public class FileSink implements LogSink {
    public FileSink(String filePath);
}

public class Logger {
    private LogLevel thresholdLevel = LogLevel.INFO;
    private final List<LogSink> sinks = new ArrayList<>();
    private final BlockingQueue<LogMessage> logQueue = new LinkedBlockingQueue<>();
    private final Thread workerThread;  // "Logger-Worker-Thread"
    private volatile boolean running = true;

    @Synchronized public static Logger getInstance();
    @Synchronized public void setThreshold(LogLevel level);
    @Synchronized public void addSink(LogSink sink);

    public void log(LogLevel level, String message); // Non-blocking: queue.offer()
    public void info(String msg);   // → log(INFO, msg)
    public void debug(String msg);  // → log(DEBUG, msg)
    public void error(String msg);  // → log(ERROR, msg)

    private void drainQueue();  // Worker thread: poll + dispatch
    @Synchronized private void dispatch(LogMessage msg); // Notifies all sinks
    public void shutdown();     // running=false + join worker thread
}
```

---

## ④ Implementation (Core Workflow)

> *"Let me walk through the key flows end to end."*

### Setup
```java
Logger logger = Logger.getInstance();

// 1. Set minimum level — messages below INFO are dropped
logger.setThreshold(LogLevel.DEBUG); // Now DEBUG and above are logged

// 2. Add output destinations
logger.addSink(new ConsoleSink());
logger.addSink(new FileSink("app.log"));
```

### Logging (Application Thread — Non-Blocking)
```java
logger.info("Application started.");
// → level(INFO) >= threshold(DEBUG) ✓
// → Creates LogMessage { timestamp=now, level=INFO, thread="main", msg="Application started." }
// → logQueue.offer(message) → RETURNS IMMEDIATELY

logger.debug("DB connection pool initialized.");
// → queued → worker thread picks it up asynchronously

logger.error("Null pointer in OrderService.");
// → queued → worker thread dispatches to both ConsoleSink and FileSink
```

### Worker Thread (Background)
```
drainQueue() loop:
  while running:
    message = logQueue.take()   ← blocks if queue empty (no busy-poll)
    dispatch(message)
      → for each sink: sink.log(message)
```

### Shutdown
```java
logger.shutdown();
// → running = false → worker thread exits drainQueue loop → join() waits for flush
```

---

## ⑤ Extensibility (Impress the Interviewer)

> *"Here's how this design handles future changes cleanly."*

| Future Requirement | How to Handle |
|---|---|
| Slack/PagerDuty alerts on ERROR | `class SlackSink implements LogSink`, add to logger |
| JSON structured logging | Override `LogMessage.toString()` to return JSON |
| Per-sink log level filter | Add `LogLevel minLevel` field to each `LogSink` implementation |
| Bounded queue (backpressure) | `new LinkedBlockingQueue<>(10_000)` — put() blocks producers when full |
| Log sampling (1-in-N) | Add sampling counter in `log()` method |

---

## 🔐 Thread-Safety Summary

| Component | Mechanism | Why |
|---|---|---|
| `logger.log()` | `BlockingQueue.offer()` | Thread-safe non-blocking enqueue |
| `drainQueue()` | `BlockingQueue.take()` | Thread-safe blocking dequeue |
| `addSink()` | `@Synchronized` | Prevent ConcurrentModificationException during iteration |
| `dispatch()` | `@Synchronized` | Protect sink list during concurrent sink additions |
| `Logger.getInstance()` | `@Synchronized` | Safe singleton initialization |
