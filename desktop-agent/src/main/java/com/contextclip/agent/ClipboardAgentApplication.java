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
        warnIfCredentialsMissing();

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

    private static void warnIfCredentialsMissing() {
        String username = System.getenv("AGENT_USERNAME");
        String password = System.getenv("AGENT_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            System.err.println("=================================");
            System.err.println("WARNING: AGENT_USERNAME or AGENT_PASSWORD is not set.");
            System.err.println("Clipboard data will NOT be sent to a secured backend.");
            System.err.println("Set both env vars and restart the agent to enable capture.");
            System.err.println("=================================");
        }
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

        String backendId = BACKEND_CLIENT.sendClipboardContent(content);
        if (backendId != null) {
            System.out.println("Clipboard sent to backend successfully.");
            System.out.println("Backend ID: " + backendId);
        } else {
            System.out.println("Backend unavailable. Clipboard captured locally but was not sent.");
        }
        System.out.flush();
    }
}
