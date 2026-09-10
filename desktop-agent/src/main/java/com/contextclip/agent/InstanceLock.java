package com.contextclip.agent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Manages single-instance enforcement for the ContextClip Desktop Agent using a
 * localhost-bound ServerSocket as a cooperative lock.
 *
 * <p>When the first agent process starts, it binds a ServerSocket on the IPC port
 * (default 57324, configurable via {@code CONTEXTCLIP_IPC_PORT}). Subsequent processes
 * that fail to bind detect the primary instance and forward their command via a simple
 * text protocol over TCP loopback, then exit immediately.</p>
 *
 * <h2>IPC Protocol (plain text, one exchange per connection)</h2>
 * <pre>
 *   Client → "PAIR:contextclip://pair?code=pair_..."
 *   Server → "OK" or "ERROR:reason"
 * </pre>
 *
 * <p><strong>Security:</strong> The server binds exclusively to 127.0.0.1 (loopback).
 * No JWTs or credentials are transmitted — only the short-lived single-use pairing code URI.
 * Incoming messages exceeding {@value #MAX_MESSAGE_BYTES} bytes are rejected.</p>
 */
public class InstanceLock {

    /** Default IPC port. Override with {@code CONTEXTCLIP_IPC_PORT} env var. */
    public static final int DEFAULT_IPC_PORT = 57324;

    /** Maximum permitted IPC message length to prevent abuse. */
    private static final int MAX_MESSAGE_BYTES = 4096;

    /** Connect/read timeout for secondary-to-primary IPC requests (ms). */
    private static final int FORWARD_TIMEOUT_MS = 5000;

    /** Command prefix for pairing requests sent over IPC. */
    private static final String CMD_PAIR = "PAIR:";

    public enum Role { PRIMARY, SECONDARY }

    private final int port;
    private volatile ServerSocket serverSocket;
    private volatile Thread ipcThread;

    public InstanceLock() {
        this(resolvePort());
    }

    public InstanceLock(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    // -------------------------------------------------------------------------
    // Port resolution
    // -------------------------------------------------------------------------

    /**
     * Returns the configured IPC port.
     * Respects the {@code CONTEXTCLIP_IPC_PORT} environment variable for testing and
     * advanced deployments.
     */
    public static int resolvePort() {
        String env = System.getenv("CONTEXTCLIP_IPC_PORT");
        if (env != null && !env.isBlank()) {
            try {
                int port = Integer.parseInt(env.trim());
                if (port > 0 && port < 65536) {
                    return port;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULT_IPC_PORT;
    }

    // -------------------------------------------------------------------------
    // Acquisition
    // -------------------------------------------------------------------------

    /**
     * Attempts to become the primary instance by binding the IPC port.
     *
     * @return {@link Role#PRIMARY} if the port was successfully bound;
     *         {@link Role#SECONDARY} if the port is already in use by a running instance.
     */
    public Role tryAcquire() {
        try {
            ServerSocket bound = new ServerSocket(this.port, 50, InetAddress.getByName("127.0.0.1"));
            this.serverSocket = bound;
            System.out.println("[IPC] Acquired single-instance lock on port " + this.port + ".");
            return Role.PRIMARY;
        } catch (IOException e) {
            // Port already in use — primary is running
            System.out.println("[IPC] Existing instance detected on port " + this.port + ".");
            return Role.SECONDARY;
        }
    }

    // -------------------------------------------------------------------------
    // IPC server (primary instance)
    // -------------------------------------------------------------------------

    /**
     * Starts the IPC listener thread on the primary instance.
     * Incoming PAIR commands are dispatched to {@code onPairRequest} on the IPC thread.
     *
     * @param onPairRequest callback invoked with the full pairing URI when a PAIR command arrives
     */
    public void startIpcServer(Consumer<String> onPairRequest) {
        if (serverSocket == null || serverSocket.isClosed()) {
            System.err.println("[IPC] Cannot start IPC server: socket not acquired.");
            return;
        }
        Thread t = new Thread(() -> runIpcServer(onPairRequest), "contextclip-ipc-server");
        t.setDaemon(true);
        t.start();
        this.ipcThread = t;
    }

    private void runIpcServer(Consumer<String> onPairRequest) {
        System.out.println("[IPC] IPC server listening for commands.");
        while (!serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                // Handle each incoming connection on an inline thread to avoid blocking the accept loop
                Thread handler = new Thread(() -> handleClient(client, onPairRequest), "contextclip-ipc-handler");
                handler.setDaemon(true);
                handler.start();
            } catch (SocketTimeoutException ignored) {
                // Normal timeout during accept — loop again
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("[IPC] IPC server accept error: " + e.getMessage());
                }
                // If the socket was closed intentionally (shutdown), exit quietly
                break;
            }
        }
        System.out.println("[IPC] IPC server stopped.");
    }

    private void handleClient(Socket client, Consumer<String> onPairRequest) {
        try {
            client.setSoTimeout(3000);
            try (
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true, StandardCharsets.UTF_8)
            ) {
                String message = in.readLine();
                if (message == null || message.isBlank()) {
                    out.println("ERROR:EMPTY_COMMAND");
                    return;
                }
                message = message.trim();

                if (message.startsWith(CMD_PAIR)) {
                    String uri = message.substring(CMD_PAIR.length()).trim();
                    if (uri.isBlank() || (!uri.startsWith("contextclip://") && !uri.startsWith("pair_"))) {
                        out.println("ERROR:INVALID_URI");
                        return;
                    }
                    System.out.println("[IPC] Received PAIR request from secondary instance.");
                    out.println("OK");
                    // Dispatch the pairing request asynchronously so we don't block the IPC handler
                    Thread pairThread = new Thread(() -> onPairRequest.accept(uri), "contextclip-ipc-pair");
                    pairThread.setDaemon(true);
                    pairThread.start();
                } else {
                    System.err.println("[IPC] Unknown IPC command (first 64 chars): "
                            + message.substring(0, Math.min(64, message.length())));
                    out.println("ERROR:UNKNOWN_COMMAND");
                }
            }
        } catch (SocketTimeoutException e) {
            System.err.println("[IPC] IPC client timed out before sending a command.");
        } catch (IOException e) {
            System.err.println("[IPC] IPC client I/O error: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // IPC client (secondary instance)
    // -------------------------------------------------------------------------

    /**
     * Forwards a pairing URI to the running primary instance.
     *
     * @param uri the full pairing URI (e.g. {@code contextclip://pair?code=pair_...})
     * @return {@code true} if the primary acknowledged the request
     */
    public boolean forwardToExistingInstance(String uri) {
        try (
            Socket socket = new Socket();
        ) {
            socket.connect(
                new java.net.InetSocketAddress(InetAddress.getByName("127.0.0.1"), this.port),
                FORWARD_TIMEOUT_MS
            );
            socket.setSoTimeout(FORWARD_TIMEOUT_MS);

            try (
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            ) {
                // Validate message length before sending
                String command = CMD_PAIR + uri;
                if (command.length() > MAX_MESSAGE_BYTES) {
                    System.err.println("[IPC] Pairing URI exceeds maximum IPC message length — cannot forward.");
                    return false;
                }
                out.println(command);

                String response = in.readLine();
                if ("OK".equals(response)) {
                    System.out.println("[IPC] Pairing request forwarded to running instance successfully.");
                    return true;
                } else {
                    System.err.println("[IPC] Primary instance returned: " + response);
                    return false;
                }
            }
        } catch (IOException e) {
            System.err.println("[IPC] Failed to forward to existing instance: " + e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Shutdown
    // -------------------------------------------------------------------------

    /**
     * Releases the IPC server socket, allowing the next process to become primary.
     * Safe to call multiple times.
     */
    public void release() {
        ServerSocket ss = this.serverSocket;
        if (ss != null && !ss.isClosed()) {
            try {
                ss.close();
                System.out.println("[IPC] Instance lock released.");
            } catch (IOException e) {
                System.err.println("[IPC] Error releasing instance lock: " + e.getMessage());
            }
        }
    }
}
