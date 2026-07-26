package logger;

import lombok.Getter;
import java.time.LocalDateTime;

@Getter
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

    @Override
    public String toString() {
        return String.format("[%s] [%s] [%s] - %s", 
                timestamp, level, threadName, message);
    }
}
