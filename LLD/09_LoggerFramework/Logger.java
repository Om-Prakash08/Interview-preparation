package logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Logger {
    private static Logger instance;
    private LogLevel thresholdLevel = LogLevel.INFO;
    private final List<LogSink> sinks = new ArrayList<>();
    private final BlockingQueue<LogMessage> logQueue = new LinkedBlockingQueue<>();
    private final Thread workerThread;
    private volatile boolean running = true;

    private Logger() {
        this.workerThread = new Thread(this::drainQueue);
        workerThread.setName("Logger-Worker-Thread");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public static synchronized Logger getInstance() {
        if (instance == null) instance = new Logger();
        return instance;
    }

    public synchronized void setThreshold(LogLevel level) { this.thresholdLevel = level; }
    public synchronized void addSink(LogSink sink)        { sinks.add(sink); }

    public void log(LogLevel level, String message) {
        if (level.ordinal() >= thresholdLevel.ordinal()) logQueue.offer(new LogMessage(level, message));
    }

    public void info(String message)  { log(LogLevel.INFO, message); }
    public void debug(String message) { log(LogLevel.DEBUG, message); }
    public void error(String message) { log(LogLevel.ERROR, message); }

    private void drainQueue() {
        try {
            while (running || !logQueue.isEmpty()) {
                LogMessage msg = logQueue.poll();
                if (msg != null) dispatch(msg);
                else Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void dispatch(LogMessage message) {
        for (LogSink sink : sinks) sink.log(message);
    }

    public void shutdown() {
        running = false;
        try { workerThread.join(2000); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
