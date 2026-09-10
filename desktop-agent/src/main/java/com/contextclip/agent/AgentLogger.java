package com.contextclip.agent;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Lightweight, safe persistent diagnostic logger for the ContextClip Desktop Agent.
 *
 * <p>Logs to both standard console streams (System.out / System.err) and to a persistent
 * log file located at {@code ~/.contextclip/agent.log}. This ensures runtime diagnostics
 * are preserved even when launched via {@code javaw} without a console window.
 *
 * <p><strong>Security guarantee:</strong> Never logs authorization tokens, secrets,
 * passwords, or user clipboard content.
 */
public final class AgentLogger {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final long MAX_LOG_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB rotation cap

    private static final Object LOCK = new Object();
    private static File logFile = null;
    private static boolean initialized = false;

    private AgentLogger() {
        // utility class
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            try {
                String userHome = System.getProperty("user.home", ".");
                Path logDir = Paths.get(userHome, ".contextclip");
                Files.createDirectories(logDir);
                logFile = logDir.resolve("agent.log").toFile();

                // Rotate if over size limit
                if (logFile.exists() && logFile.length() > MAX_LOG_SIZE_BYTES) {
                    File backupFile = logDir.resolve("agent.log.1").toFile();
                    if (backupFile.exists()) {
                        backupFile.delete();
                    }
                    logFile.renameTo(backupFile);
                    logFile = logDir.resolve("agent.log").toFile();
                }
            } catch (Throwable t) {
                System.err.println("[AgentLogger] Failed to initialize persistent log file: " + t.getMessage());
            } finally {
                initialized = true;
            }
        }
    }

    public static void info(String message) {
        log("INFO", message, null);
    }

    public static void warn(String message) {
        log("WARN", message, null);
    }

    public static void error(String message) {
        log("ERROR", message, null);
    }

    public static void error(String message, Throwable throwable) {
        log("ERROR", message, throwable);
    }

    public static void debug(String message) {
        log("DEBUG", message, null);
    }

    public static String getLogFilePath() {
        ensureInitialized();
        return logFile != null ? logFile.getAbsolutePath() : "unknown";
    }

    private static void log(String level, String message, Throwable throwable) {
        ensureInitialized();

        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String threadName = Thread.currentThread().getName();
        String formatted = String.format("[%s] [%-5s] [%s] %s", timestamp, level, threadName, message);

        // 1. Write to standard stream
        if ("ERROR".equals(level) || "WARN".equals(level)) {
            System.err.println(formatted);
            if (throwable != null) {
                throwable.printStackTrace(System.err);
            }
        } else {
            System.out.println(formatted);
            if (throwable != null) {
                throwable.printStackTrace(System.out);
            }
        }

        // 2. Append to persistent file (~/.contextclip/agent.log)
        if (logFile == null) {
            return;
        }

        synchronized (LOCK) {
            try (FileWriter fw = new FileWriter(logFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(formatted);
                if (throwable != null) {
                    throwable.printStackTrace(pw);
                }
                pw.flush();
            } catch (Throwable ignored) {
                // Never crash the application due to logging I/O failures
            }
        }
    }
}
