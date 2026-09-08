package com.contextclip.agent;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;

/**
 * Entry point for the ContextClip Desktop Agent.
 * Integrates automatic monitoring, Windows System Tray, pause/resume lifecycle,
 * and Windows Startup automation.
 */
public class ClipboardAgentApplication {

    private static final CountDownLatch KEEP_ALIVE_LATCH = new CountDownLatch(1);
    private static BackendClient backendClient;
    private static ClipboardMonitor clipboardMonitor;
    private static TrayManager trayManager;

    public static void main(String[] args) {
        if (args != null && args.length > 0) {
            if (handleCommandLineArgs(args)) {
                return;
            }
        }

        printBanner();

        AgentConfig config = AgentConfig.load();
        if (!validateConfig(config)) {
            return;
        }

        backendClient = new BackendClient(config);

        // Initialize ClipboardMonitor
        clipboardMonitor = new ClipboardMonitor(ClipboardAgentApplication::handleClipboardContent);

        // Initialize TrayManager
        trayManager = new TrayManager(
            paused -> {
                if (paused) {
                    clipboardMonitor.pause();
                    System.out.println("Clipboard monitoring paused.");
                } else {
                    clipboardMonitor.resume();
                    System.out.println("Clipboard monitoring resumed.");
                }
            },
            () -> {
                System.out.println("Exit requested from System Tray.");
                shutdown();
                System.exit(0);
            }
        );
        trayManager.initialize();

        // Register shutdown hook for graceful exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown();
            System.out.println("=================================");
            System.out.println("Clipboard monitoring stopped.");
            System.out.println("Goodbye!");
            System.out.println("=================================");
        }));

        try {
            clipboardMonitor.start();
            trayManager.updateStatus(ConnectionStatus.CONNECTED);
            // Block main thread until shutdown signal
            KEEP_ALIVE_LATCH.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Failed to start clipboard agent: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    private static boolean handleCommandLineArgs(String[] args) {
        String command = args[0].toLowerCase();
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
                return false;
            }
        }
    }

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
            String jarPath = new File(ClipboardAgentApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
            String scriptContent = "@echo off\r\nstart \"\" javaw -jar \"" + jarPath + "\"\r\n";
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
            System.err.println("2. Call POST /api/auth/agent-token with your user JWT to get an agent JWT.");
            System.err.println("3. Configure the token in one of these ways:");
            System.err.println("   Option A: Set the AGENT_TOKEN environment variable:");
            System.err.println("     $env:AGENT_TOKEN=\"<YOUR_PERSONAL_AGENT_TOKEN>\"");
            System.err.println("   Option B: Save to user config (~/.contextclip/agent.properties):");
            System.err.println("     java -jar desktop-agent.jar --set-token <YOUR_PERSONAL_AGENT_TOKEN>");
            System.err.println("4. Restart the desktop agent.");
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
            System.err.println("1. Log in to ContextClip and obtain your user JWT via POST /api/auth/login.");
            System.err.println("2. Call POST /api/auth/agent-token with Authorization: Bearer <user-JWT>.");
            System.err.println("3. Copy the returned token value.");
            System.err.println("4. Set: $env:AGENT_TOKEN=\"<FRESH_AGENT_JWT>\"");
            System.err.println("5. Restart the desktop agent.");
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

    private static void printBanner() {
        System.out.println("=================================");
        System.out.println("        CONTEXTCLIP AGENT");
        System.out.println("=================================");
        System.out.println("Clipboard monitoring active.");
        System.out.println("Use the system tray icon to pause or exit.");
        System.out.flush();
    }

    private static void handleClipboardContent(String content) {
        System.out.println("[Agent] Clipboard changed (" + (content != null ? content.length() : 0) + " chars detected)");

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

    private static synchronized void shutdown() {
        if (clipboardMonitor != null) {
            clipboardMonitor.stop();
        }
        if (trayManager != null) {
            trayManager.shutdown();
        }
        KEEP_ALIVE_LATCH.countDown();
    }
}
