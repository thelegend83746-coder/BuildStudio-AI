package com.apk.builder.logger;

import java.util.ArrayList;
import java.util.List;

public class Logger {
    public interface LogListener {
        void onLog(String message);
    }

    private static final List<LogListener> listeners = new ArrayList<>();
    private static final StringBuilder logBuffer = new StringBuilder();

    public static synchronized void addListener(LogListener listener) {
        listeners.add(listener);
    }

    public static synchronized void removeListener(LogListener listener) {
        listeners.remove(listener);
    }

    public static synchronized void log(String message) {
        logBuffer.append(message).append("\n");
        for (LogListener l : listeners) {
            l.onLog(message);
        }
    }

    public static synchronized void clear() {
        logBuffer.setLength(0);
    }

    public static synchronized String getLogs() {
        return logBuffer.toString();
    }
}
