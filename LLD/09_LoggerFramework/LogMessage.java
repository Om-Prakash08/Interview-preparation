package logger;

import java.time.LocalDateTime;

public class LogMessage {
    private final LocalDateTime timestamp;
    private final LogLevel level;
    private final String message;
    private final String threadName;

    public LogMessage(LogLevel level, String message) {
        this.timestamp = LocalDateTime.now();
        this.level = level;
        this.message = message;
        this.threadName = Thread.currentThread().getName();
    }

    public LocalDateTime getTimestamp() { return timestamp; }
    public LogLevel getLevel()          { return level; }
    public String getMessage()          { return message; }
    public String getThreadName()       { return threadName; }

    @Override
    public String toString() {
        return String.format("[%s] [%s] [%s] - %s", timestamp, level, threadName, message);
    }
}
