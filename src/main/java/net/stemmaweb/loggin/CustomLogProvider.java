package net.stemmaweb.loggin;

import org.neo4j.logging.Log;
import org.neo4j.logging.LogProvider;

public class CustomLogProvider implements LogProvider {
    @Override
    public Log getLog(Class<?> loggingClass) {
        return new CustomLog(loggingClass.getSimpleName());
    }

    @Override
    public Log getLog(String context) {
        return new CustomLog(context);
    }
}
