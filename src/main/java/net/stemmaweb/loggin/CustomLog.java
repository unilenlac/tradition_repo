package net.stemmaweb.loggin;

import org.neo4j.logging.Log;
import org.neo4j.logging.LogProvider;

import java.io.PrintWriter;
import java.io.StringWriter;

// Custom Log implementation
class CustomLog implements Log {
    private final String context;

    public CustomLog(String context) {
        this.context = context;
    }

    @Override
    public boolean isDebugEnabled() {
        return true; // Enable debug logging
    }

    @Override
    public void debug(String message) {
        System.out.println("[DEBUG] [" + context + "] " + message);
    }

    @Override
    public void debug(String message, Throwable throwable) {
        System.out.println("[DEBUG] [" + context + "] " + message + " - " + getStackTrace(throwable));
    }

    @Override
    public void debug(String format, Object... arguments) {

    }

    @Override
    public void info(String message) {
        System.out.println("[INFO] [" + context + "] " + message);
    }

    @Override
    public void info(String message, Throwable throwable) {
        System.out.println("[INFO] [" + context + "] " + message + " - " + getStackTrace(throwable));
    }

    @Override
    public void info(String format, Object... arguments) {

    }

    @Override
    public void warn(String message) {
        System.err.println("[WARN] [" + context + "] " + message);
    }

    @Override
    public void warn(String message, Throwable throwable) {
        System.err.println("[WARN] [" + context + "] " + message + " - " + getStackTrace(throwable));
    }

    @Override
    public void warn(String format, Object... arguments) {

    }

    @Override
    public void error(String message) {
        System.err.println("[ERROR] [" + context + "] " + message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        System.err.println("[ERROR] [" + context + "] " + message + " - " + getStackTrace(throwable));
    }

    @Override
    public void error(String format, Object... arguments) {

    }

    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}