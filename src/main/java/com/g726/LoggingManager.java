package com.g726;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class LoggingManager {
    private static final Logger LOGGER = Logger.getLogger("GSaveManager");
    private static boolean initialized = false;

    private LoggingManager() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        try {
            Path logDir = Paths.get("logs");
            Files.createDirectories(logDir);
            Path logFile = logDir.resolve("app.log");

            FileHandler fileHandler = new FileHandler(logFile.toString(), true);
            fileHandler.setEncoding(StandardCharsets.UTF_8.name());
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.INFO);

            LOGGER.setLevel(Level.INFO);
            LOGGER.addHandler(fileHandler);

            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                    LOGGER.log(Level.SEVERE, "Unhandled exception in thread: " + thread.getName(), throwable));

            initialized = true;
        } catch (IOException e) {
            System.err.println("无法初始化日志系统: " + e.getMessage());
        }
    }

    public static Logger getLogger() {
        return LOGGER;
    }
}
