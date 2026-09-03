package com.contextclip.agent;

import java.util.concurrent.CountDownLatch;

/**
 * Entry point for the ContextClip Desktop Agent.
 */
public class ClipboardAgentApplication {

    private static final CountDownLatch KEEP_ALIVE_LATCH = new CountDownLatch(1);
    private static final BackendClient BACKEND_CLIENT = new BackendClient();

    public static void main(String[] args) {
        printBanner();
        if (!validateAgentToken()) {
            return;
        }

        ClipboardMonitor monitor = new ClipboardMonitor(ClipboardAgentApplication::handleClipboardContent);

        // Register shutdown hook for graceful exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println();
            System.out.println("=================================");
            System.out.println("Stopping ContextClip Agent...");
            monitor.stop();
            KEEP_ALIVE_LATCH.countDown();
            System.out.println("Clipboard monitoring stopped.");
            System.out.println("Goodbye!");
            System.out.println("=================================");
        }));

        try {
            monitor.start();
            // Block main thread until shutdown signal
            KEEP_ALIVE_LATCH.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("Failed to start clipboard agent: " + e.getMessage());
            e.printStackTrace();
        } finally {
            monitor.stop();
        }
    }

    /**
     * Validates that AGENT_TOKEN is set and has JWT structure (header.payload.signature).
     * Does NOT fall back to contextclip-agent or any shared credentials.
     */
    private static boolean validateAgentToken() {
        String token = System.getenv("AGENT_TOKEN");
        if (token == null || token.isBlank()) {
            token = System.getenv("DESKTOP_AGENT_TOKEN");
        }
        if (token == null || token.isBlank()) {
            System.err.println("=================================================================");
            System.err.println("ERROR: Personal user AGENT_TOKEN is not set.");
            System.err.println("A personal user AGENT_TOKEN is required for clipboard capture.");
            System.err.println("Clipboard data will NOT be sent to a shared or unauthenticated account.");
            System.err.println();
            System.err.println("To connect the desktop agent to your account:");
            System.err.println("1. Log in to ContextClip in a browser at http://localhost:3000");
            System.err.println("2. Call POST /api/auth/agent-token with your user JWT to get an agent JWT.");
            System.err.println("3. Set the AGENT_TOKEN environment variable:");
            System.err.println("     $env:AGENT_TOKEN=\"<YOUR_PERSONAL_AGENT_TOKEN>\"");
            System.err.println("4. Restart the desktop agent.");
            System.err.println("=================================================================");
            return false;
        }

        // Validate JWT structure: must have exactly two '.' separators (header.payload.signature)
        long dotCount = token.chars().filter(c -> c == '.').count();
        if (dotCount != 2) {
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

        System.out.println("Authenticated via configured personal AGENT_TOKEN.");
        return true;
    }

    private static void printBanner() {
        System.out.println("=================================");
        System.out.println("        CONTEXTCLIP AGENT");
        System.out.println("=================================");
        System.out.println("Clipboard monitoring started.");
        System.out.println("Copy text anywhere to test.");
        System.out.println("Press Ctrl+C in this terminal to stop.");
        System.out.flush();
    }

    private static void handleClipboardContent(String content) {
        System.out.println("---------------------------------");
        System.out.println("Clipboard Changed");
        System.out.println("---------------------------------");
        System.out.println("Content:");
        System.out.println(content);
        System.out.println("---------------------------------");

        BackendClient.SendResult result = BACKEND_CLIENT.sendClipboardContent(content);

        if (result.isSuccess()) {
            System.out.println("Clipboard sent to backend successfully.");
            System.out.println("Backend ID: " + result.backendId);
        } else {
            switch (result.errorType) {
                case HTTP_401 -> {
                    System.err.println("ERROR 401 Unauthorized: The AGENT_TOKEN was rejected by the backend.");
                    System.err.println("Your token may be expired or invalid. Generate a fresh AGENT_TOKEN:");
                    System.err.println("  1. POST /api/auth/login  -> user JWT");
                    System.err.println("  2. POST /api/auth/agent-token (Bearer <user-JWT>) -> agent JWT");
                    System.err.println("  3. $env:AGENT_TOKEN=\"<agent-JWT>\"  then restart the agent.");
                }
                case HTTP_403 -> System.err.println("ERROR 403 Forbidden: The backend denied access. ROLE_AGENT may not be authorized for this operation.");
                case HTTP_429 -> System.err.println("ERROR 429 Too Many Requests: Backend rate limit reached. Clipboard captured locally but was not sent.");
                case HTTP_5XX -> System.err.println("ERROR " + result.httpStatus + " Server Error: Backend returned a server error. Clipboard captured locally but was not sent.");
                case HTTP_OTHER -> System.err.println("ERROR " + result.httpStatus + ": Backend returned an unexpected status. Clipboard captured locally but was not sent.");
                default -> System.out.println("Backend unavailable. Clipboard captured locally but was not sent.");
            }
        }
        System.out.flush();
    }
}
