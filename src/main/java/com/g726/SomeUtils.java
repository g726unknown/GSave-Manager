package com.g726;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class SomeUtils {
    private static final Logger LOGGER = Logger.getLogger("GSaveManager");
    private static boolean logInitialized = false;
    private static final DateTimeFormatter ARCHIVE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DISPLAY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static synchronized void initLogging() {
        if (logInitialized) {
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

            logInitialized = true;
        } catch (IOException e) {
            System.err.println("无法初始化日志系统: " + e.getMessage());
        }
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    public static String formatTimestampForDisplay(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return "";
        }
        try {
            LocalDateTime parsed = LocalDateTime.parse(timestamp, ARCHIVE_TIMESTAMP);
            return parsed.format(DISPLAY_TIMESTAMP);
        } catch (Exception ex) {
            return timestamp;
        }
    }

    public static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                if (Files.notExists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void deleteDirectory(Path pathToBeDeleted) throws IOException {
        if (Files.notExists(pathToBeDeleted)) {
            return;
        }

        Files.walkFileTree(pathToBeDeleted, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                file.toFile().setWritable(true);
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void openFolder(Path path) {
        try {
            if (Files.exists(path)) {
                if (java.awt.Desktop.isDesktopSupported()) {
                    java.awt.Desktop.getDesktop().open(path.toFile());
                } else {
                    System.out.println("当前系统不支持自动打开文件夹功能。");
                }
            } else {
                System.out.println("无法打开：文件夹不存在 (" + path.toString() + ")");
            }
        } catch (IOException e) {
            System.err.println("打开文件夹时发生错误: " + e.getMessage());
        }
    }
}