package logger;

public interface LogSink {
    void log(LogMessage message);
}

class ConsoleSink implements LogSink {
    @Override
    public void log(LogMessage message) {
        System.out.println("[ConsoleAppender] " + message);
    }
}

class FileSink implements LogSink {
    @Override
    public void log(LogMessage message) {
        // Simulating writing to file system
        System.out.println("[FileAppender] [log.txt] " + message);
    }
}
