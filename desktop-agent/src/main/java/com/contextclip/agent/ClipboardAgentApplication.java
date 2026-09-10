package com.contextclip.agent;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Entry point for the ContextClip Desktop Agent.
 * Integrates automatic monitoring, Windows System Tray, pause/resume lifecycle,
 * Windows Startup automation, and Phase 16 single-instance enforcement via IPC.
 *
 * <h2>Single-instance flow</h2>
 * <ol>
 *   <li>On startup, attempt to acquire the InstanceLock (bind IPC port 57324).</li>
 *   <li>If the port is already bound (SECONDARY): forward the command to the primary
 *       instance over TCP loopback, then exit immediately — no tray, no monitor.</li>
 *   <li>If the port is free (PRIMARY): start the IPC server, proceed with full
 *       initialization (tray, clipboard monitor, backend client).</li>
 * </ol>
 */
public class ClipboardAgentApplication {

    private static final CountDownLatch KEEP_ALIVE_LATCH = new CountDownLatch(1);
    private static BackendClient backendClient;
    private static ClipboardMonitor clipboardMonitor;
    private static TrayManager trayManager;

    /** Bounded queue decoupling clipboard capture from HTTP transmission. */
    private static final BlockingQueue<String> uploadQueue = new LinkedBlockingQueue<>(100);
    private static final AtomicBoolean uploaderRunning = new AtomicBoolean(false);
    private static Thread uploaderThread;

    /** Single-instance lock — released on shutdown. */
    private static final InstanceLock instanceLock = new InstanceLock();

    public static void main(String[] args) {
        // Disable broken / missing Java Access Bridge assistive technologies to prevent AWTError
        try {
            if (System.getProperty("javax.accessibility.assistive_technologies") == null) {
                System.setProperty("javax.accessibility.assistive_technologies", "");
            }
        } catch (Throwable ignored) {
        }

        // Install default uncaught exception handler to capture unhandled errors in agent.log
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            AgentLogger.error("Uncaught exception on thread " + t.getName() + ": " + e.getMessage(), e);
        });

        AgentLogger.info("=== ContextClip Desktop Agent Startup ===");
        AgentLogger.info("Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
        AgentLogger.info("Java Home: " + System.getProperty("java.home"));
        AgentLogger.info("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
        AgentLogger.info("Working Dir: " + System.getProperty("user.dir"));
        AgentLogger.info("Log File: " + AgentLogger.getLogFilePath());

        // ----------------------------------------------------------------
        // Phase 16: Single-instance enforcement
        // ----------------------------------------------------------------
        InstanceLock.Role role = instanceLock.tryAcquire();

        if (role == InstanceLock.Role.SECONDARY) {
            AgentLogger.info("[IPC] Secondary instance detected. Instance lock held by running primary instance.");
            if (args != null && args.length > 0) {
                String firstArg = cleanQuotes(args[0]);
                if (firstArg.startsWith("contextclip://") || firstArg.startsWith("pair_")) {
                    AgentLogger.info("[IPC] Forwarding pairing request to running primary instance...");
                    boolean forwarded = instanceLock.forwardToExistingInstance(firstArg);
                    if (!forwarded) {
                        AgentLogger.error("[IPC] Could not forward pairing request to running primary instance.");
                    } else {
                        AgentLogger.info("[IPC] Successfully forwarded pairing request.");
                    }
                } else {
                    AgentLogger.info("[IPC] Desktop Agent is already running. Managed via system tray.");
                }
            } else {
                AgentLogger.info("[IPC] Desktop Agent is already running. Managed via system tray.");
            }
            return;
        }

        AgentLogger.info("[IPC] Primary instance role acquired. Initializing Desktop Agent services...");

        // PRIMARY: handle CLI args before proceeding to normal startup
        if (args != null && args.length > 0) {
            if (handleCommandLineArgs(args)) {
                instanceLock.release();
                return;
            }
        }

        printBanner();

        AgentConfig config = AgentConfig.load();
        if (!validateConfig(config)) {
            AgentLogger.warn("Configuration validation failed. Desktop Agent exiting.");
            instanceLock.release();
            return;
        }

        backendClient = new BackendClient(config);

        // Initialize TrayManager first so tray icon appears immediately on primary startup
        try {
            trayManager = new TrayManager(
                paused -> {
                    if (paused) {
                        if (clipboardMonitor != null) {
                            clipboardMonitor.pause();
                        }
                        AgentLogger.info("Clipboard monitoring paused.");
                    } else {
                        if (clipboardMonitor != null) {
                            clipboardMonitor.resume();
                        }
                        AgentLogger.info("Clipboard monitoring resumed.");
                    }
                },
                () -> {
                    AgentLogger.info("Exit requested from System Tray.");
                    shutdown();
                    System.exit(0);
                }
            );
            trayManager.initialize();
        } catch (Throwable t) {
            AgentLogger.error("Failed to initialize System Tray: " + t.getMessage(), t);
        }

        // Initialize ClipboardMonitor safely
        try {
            clipboardMonitor = new ClipboardMonitor(ClipboardAgentApplication::handleClipboardContent);
        } catch (Throwable t) {
            AgentLogger.error("Failed to initialize ClipboardMonitor: " + t.getMessage(), t);
        }

        // Phase 16: Start IPC server so secondary instances can forward commands
        instanceLock.startIpcServer(ClipboardAgentApplication::handleIpcPairingRequest);

        // Register shutdown hook for graceful exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown();
            AgentLogger.info("=================================");
            AgentLogger.info("Clipboard monitoring stopped.");
            AgentLogger.info("Goodbye!");
            AgentLogger.info("=================================");
        }));

        try {
            uploaderRunning.set(true);
            uploaderThread = new Thread(ClipboardAgentApplication::processUploadQueue, "contextclip-uploader");
            uploaderThread.setDaemon(true);
            uploaderThread.start();
            AgentLogger.info("Background upload worker thread started.");

            if (clipboardMonitor != null) {
                clipboardMonitor.start();
                AgentLogger.info("Clipboard monitoring active.");
            }
            if (trayManager != null) {
                trayManager.updateStatus(ConnectionStatus.CONNECTED);
            }
            // Block main thread until shutdown signal
            KEEP_ALIVE_LATCH.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            AgentLogger.error("Failed to start clipboard agent: " + e.getMessage(), e);
        } finally {
            shutdown();
        }
    }

    // -------------------------------------------------------------------------
    // Phase 16: IPC pairing handler (called on the IPC thread for primary)
    // -------------------------------------------------------------------------

    /**
     * Called by the IPC server when the running primary receives a PAIR command
     * forwarded from a secondary process.
     * Performs the pairing exchange, saves the new token, and refreshes the backend client.
     */
    static void handleIpcPairingRequest(String uri) {
        System.out.println("[IPC] Processing pairing request from secondary instance...");
        AgentConfig cfg = AgentConfig.load();
        PairingHandler handler = new PairingHandler();
        PairingHandler.PairingResult result = handler.handlePairing(uri, cfg.getEndpointUrl());
        if (result.success()) {
            System.out.println("[IPC] Pairing succeeded for user: " + result.username());
            // Reload configuration and refresh the backend client with the new token
            AgentConfig freshConfig = AgentConfig.load();
            if (backendClient != null) {
                backendClient = new BackendClient(freshConfig);
                System.out.println("[IPC] Backend client refreshed with new AGENT token.");
            }
            if (trayManager != null) {
                trayManager.updateStatus(ConnectionStatus.CONNECTED);
            }
            System.out.println("[IPC] Agent is now CONNECTED. Clipboard monitoring continues.");
        } else {
            System.err.println("[IPC] Pairing failed: " + result.message());
            if (trayManager != null) {
                trayManager.updateStatus(ConnectionStatus.UNAUTHORIZED);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private static String cleanQuotes(String s) {
        if (s == null || s.isBlank()) return s;
        String trimmed = s.trim();
        while ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
               (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            if (trimmed.length() < 2) break;
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /**
     * Handles explicit CLI commands (--diagnose-auth, --pair, etc.).
     *
     * @return true if the process should exit after handling the command,
     *         false if normal monitoring startup should proceed.
     */
    private static boolean handleCommandLineArgs(String[] args) {
        String firstArg = cleanQuotes(args[0]);
        String command = firstArg.toLowerCase();
        switch (command) {
            case "--diagnose-auth" -> {
                diagnoseAuth();
                return true;
            }
            case "--install-startup" -> {
                installStartup();
                return true;
            }
            case "--uninstall-startup" -> {
                uninstallStartup();
                return true;
            }
            case "--pair" -> {
                if (args.length < 2 || args[1].isBlank()) {
                    System.err.println("Usage: --pair <PAIRING_CODE_OR_URI>");
                    return true;
                }
                String uriOrCode = cleanQuotes(args[1]);
                AgentConfig cfg = AgentConfig.load();
                PairingHandler handler = new PairingHandler();
                System.out.println("Processing pairing request...");
                PairingHandler.PairingResult result = handler.handlePairing(uriOrCode, cfg.getEndpointUrl());
                if (result.success()) {
                    System.out.println("SUCCESS: " + result.message());
                    System.out.println("Agent configured successfully at: " + AgentConfig.getUserConfigFile().getAbsolutePath());
                } else {
                    System.err.println("ERROR: " + result.message());
                }
                return true;
            }
            case "--set-token" -> {
                if (args.length < 2 || args[1].isBlank()) {
                    System.err.println("Usage: --set-token <AGENT_TOKEN>");
                    return true;
                }
                String token = AgentConfig.stripQuotes(args[1].trim());
                if (!AgentConfig.isJwtStructured(token)) {
                    System.err.println("ERROR: The provided token is not a valid 3-part JWT or contains invalid characters.");
                    return true;
                }
                boolean saved = AgentConfig.saveUserToken(token);
                if (saved) {
                    System.out.println("AGENT_TOKEN saved successfully to user configuration: "
                            + AgentConfig.getUserConfigFile().getAbsolutePath());
                    AgentConfig.JwtClaimsSummary summary = AgentConfig.inspectTokenMetadata(token);
                    System.out.println("Saved token metadata -> role: " + summary.role
                            + ", subject: " + summary.subject
                            + ", length: " + summary.tokenLength);
                }
                return true;
            }
            default -> {
                // Phase 15 direct protocol URI invocation
                // Phase 16: if a primary is already running (which we checked above),
                // this path only runs when WE are the primary and started WITH a URI argument.
                // In that case: pair first, then continue to monitoring.
                if (firstArg.startsWith("contextclip://") || firstArg.startsWith("pair_")) {
                    AgentConfig cfg = AgentConfig.load();
                    PairingHandler handler = new PairingHandler();
                    System.out.println("Processing pairing protocol URI...");
                    PairingHandler.PairingResult result = handler.handlePairing(firstArg, cfg.getEndpointUrl());
                    if (result.success()) {
                        System.out.println("SUCCESS: " + result.message());
                        System.out.println("Agent paired successfully. Starting clipboard monitoring...");
                        // Return false so main() proceeds to validateConfig, initialize tray, and start monitoring
                        return false;
                    } else {
                        System.err.println("ERROR: " + result.message());
                        return true;
                    }
                }
                return false;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Diagnostics
    // -------------------------------------------------------------------------

    public static void diagnoseAuth() {
        AgentConfig config = AgentConfig.load();
        String token = config.getAgentToken();
        boolean present = token != null && !token.isBlank();
        AgentConfig.JwtClaimsSummary summary = AgentConfig.inspectTokenMetadata(token);

        System.out.println("=== ContextClip Desktop Agent Auth Diagnostics ===");
        System.out.println("API URL: " + config.getEndpointUrl());
        System.out.println("token source: " + (config.getTokenSource() != null ? config.getTokenSource() : "none"));
        System.out.println("token present: " + present);
        System.out.println("token length: " + (present ? token.length() : 0));
        System.out.println("JWT parts: " + summary.partsCount);
        System.out.println("JWT role: " + (summary.role != null ? summary.role : "none"));
        System.out.println("JWT subject: " + (summary.subject != null ? summary.subject : "none"));
        System.out.println("=================================================");
    }

    // -------------------------------------------------------------------------
    // Startup management
    // -------------------------------------------------------------------------

    public static boolean installStartup() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            System.err.println("Could not resolve APPDATA directory for Windows startup.");
            return false;
        }
        File startupDir = new File(appData, "Microsoft\\Windows\\Start Menu\\Programs\\Startup");
        if (!startupDir.exists()) {
            startupDir.mkdirs();
        }
        File startupFile = new File(startupDir, "ContextClipAgent.bat");
        try {
            File jarFile = new File(ClipboardAgentApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File jarDir = jarFile.getParentFile();
            File launcherCmd = new File(jarDir, "ContextClipLauncher.cmd");
            File bundledJavaw = new File(jarDir, "runtime\\bin\\javaw.exe");

            String scriptContent;
            if (launcherCmd.exists()) {
                scriptContent = "@echo off\r\nstart \"\" \"" + launcherCmd.getAbsolutePath() + "\"\r\n";
            } else if (bundledJavaw.exists()) {
                scriptContent = "@echo off\r\nstart \"\" \"" + bundledJavaw.getAbsolutePath() + "\" -jar \"" + jarFile.getAbsolutePath() + "\"\r\n";
            } else {
                scriptContent = "@echo off\r\nstart \"\" javaw -jar \"" + jarFile.getAbsolutePath() + "\"\r\n";
            }

            Files.writeString(startupFile.toPath(), scriptContent);
            System.out.println("Windows startup installed successfully: " + startupFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            System.err.println("Failed to install Windows startup: " + e.getMessage());
            return false;
        }
    }

    public static boolean uninstallStartup() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) return false;
        File startupFile = new File(appData, "Microsoft\\Windows\\Start Menu\\Programs\\Startup\\ContextClipAgent.bat");
        if (startupFile.exists()) {
            boolean deleted = startupFile.delete();
            System.out.println("Windows startup uninstalled: " + deleted);
            return deleted;
        }
        System.out.println("Windows startup file not found.");
        return true;
    }

    // -------------------------------------------------------------------------
    // Config validation
    // -------------------------------------------------------------------------

    private static boolean validateConfig(AgentConfig config) {
        String token = config.getAgentToken();
        if (token == null || token.isBlank()) {
            System.err.println("=================================================================");
            System.err.println("ERROR: Personal user AGENT_TOKEN is not set.");
            System.err.println("A personal user AGENT_TOKEN is required for clipboard capture.");
            System.err.println("Clipboard data will NOT be sent to a shared or unauthenticated account.");
            System.err.println();
            System.err.println("To connect the desktop agent to your account:");
            System.err.println("1. Log in to ContextClip in a browser at http://localhost:3000");
            System.err.println("2. Navigate to Connect Agent and click Connect Desktop.");
            System.err.println("3. The browser will launch contextclip:// automatically.");
            System.err.println("   Or manually: java -jar desktop-agent.jar --pair <CODE>");
            System.err.println("=================================================================");
            return false;
        }

        if (!AgentConfig.isJwtStructured(token)) {
            long dotCount = token.chars().filter(c -> c == '.').count();
            System.err.println("=================================================================");
            System.err.println("ERROR: AGENT_TOKEN does not look like a valid JWT.");
            System.err.println("A valid JWT has exactly three sections separated by '.' characters.");
            System.err.println("The current AGENT_TOKEN has " + dotCount + " '.' separator(s), expected 2.");
            System.err.println();
            System.err.println("The token you have set is NOT a JWT issued by the backend.");
            System.err.println("You may have a stale, random, or incorrectly formatted token.");
            System.err.println();
            System.err.println("To get a fresh agent JWT:");
            System.err.println("1. Log in to ContextClip and use Connect Desktop from the dashboard.");
            System.err.println("2. Or use the CLI fallback: --pair <PAIRING_CODE>");
            System.err.println("=================================================================");
            return false;
        }

        AgentConfig.JwtClaimsSummary summary = AgentConfig.inspectTokenMetadata(token);
        System.out.println("Authenticated via configured personal AGENT_TOKEN from " + config.getTokenSource() + ".");
        System.out.println("Agent Token Info -> role: " + (summary.role != null ? summary.role : "unknown")
                + ", subject: " + (summary.subject != null ? summary.subject : "unknown")
                + ", length: " + summary.tokenLength + " chars.");
        return true;
    }

    // -------------------------------------------------------------------------
    // Banner + clipboard callback
    // -------------------------------------------------------------------------

    private static void printBanner() {
        System.out.println("=================================");
        System.out.println("        CONTEXTCLIP AGENT");
        System.out.println("=================================");
        System.out.println("Clipboard monitoring active.");
        System.out.println("Use the system tray icon to pause or exit.");
        System.out.flush();
    }

    private static void handleClipboardContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }
        System.out.println("[Agent] Clipboard changed (" + content.length() + " chars detected)");
        while (!uploadQueue.offer(content)) {
            // Queue full: discard oldest entry to prevent unbounded buffer
            uploadQueue.poll();
        }
    }

    private static void processUploadQueue() {
        while (uploaderRunning.get() || !uploadQueue.isEmpty()) {
            try {
                String content = uploadQueue.poll(500, TimeUnit.MILLISECONDS);
                if (content != null) {
                    uploadClipboardContent(content);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable t) {
                System.err.println("[Uploader] Unexpected error in upload loop: " + t.getMessage());
            }
        }
    }

    private static void uploadClipboardContent(String content) {
        if (backendClient == null) {
            return;
        }

        BackendClient.SendResult result = backendClient.sendClipboardContent(content);

        if (result.isSuccess()) {
            System.out.println("Clipboard sent to backend successfully.");
            System.out.println("Backend ID: " + result.backendId);
            if (trayManager != null) {
                trayManager.updateStatus(ConnectionStatus.CONNECTED);
            }
        } else {
            switch (result.errorType) {
                case HTTP_401 -> {
                    System.err.println("ERROR 401 Unauthorized: Agent authentication failed. Generate a fresh personal AGENT_TOKEN.");
                    if (trayManager != null) {
                        trayManager.updateStatus(ConnectionStatus.UNAUTHORIZED);
                    }
                }
                case HTTP_403 -> {
                    System.err.println("ERROR 403 Forbidden: Agent is authenticated but is not allowed to perform this operation.");
                    if (trayManager != null) {
                        trayManager.updateStatus(ConnectionStatus.FORBIDDEN);
                    }
                }
                case HTTP_429 -> {
                    System.err.println("ERROR 429 Too Many Requests: Backend rate limit reached. Retry later.");
                    if (trayManager != null) {
                        trayManager.updateStatus(ConnectionStatus.RATE_LIMITED);
                    }
                }
                case HTTP_5XX -> {
                    System.err.println("ERROR " + result.httpStatus + " Server Error: Backend server error.");
                    if (trayManager != null) {
                        trayManager.updateStatus(ConnectionStatus.SERVER_ERROR);
                    }
                }
                case HTTP_OTHER -> {
                    System.err.println("ERROR " + result.httpStatus + ": Backend returned an unexpected status.");
                    if (trayManager != null) {
                        trayManager.updateStatus(ConnectionStatus.DISCONNECTED);
                    }
                }
                default -> {
                    System.out.println("Backend unavailable. Clipboard captured locally but was not sent.");
                    if (trayManager != null) {
                        trayManager.updateStatus(ConnectionStatus.DISCONNECTED);
                    }
                }
            }
        }
        System.out.flush();
    }

    // -------------------------------------------------------------------------
    // Shutdown
    // -------------------------------------------------------------------------

    private static synchronized void shutdown() {
        AgentLogger.info("Shutting down ContextClip Desktop Agent...");
        uploaderRunning.set(false);
        if (uploaderThread != null) {
            uploaderThread.interrupt();
        }
        if (clipboardMonitor != null) {
            try {
                clipboardMonitor.stop();
            } catch (Throwable t) {
                AgentLogger.warn("Error stopping clipboard monitor: " + t.getMessage());
            }
        }
        if (trayManager != null) {
            try {
                trayManager.shutdown();
            } catch (Throwable t) {
                AgentLogger.warn("Error shutting down tray manager: " + t.getMessage());
            }
        }
        instanceLock.release();
        KEEP_ALIVE_LATCH.countDown();
        AgentLogger.info("ContextClip Desktop Agent shutdown complete.");
    }
}
