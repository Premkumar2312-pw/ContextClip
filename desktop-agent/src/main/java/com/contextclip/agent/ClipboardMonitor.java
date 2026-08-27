package com.contextclip.agent;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorEvent;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Monitors the system clipboard for text changes using AWT FlavorListener.
 */
public class ClipboardMonitor implements FlavorListener {

    private final Clipboard clipboard;
    private final Consumer<String> onTextCopied;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private String lastProcessedText = null;

    public ClipboardMonitor(Consumer<String> onTextCopied) {
        this(getSystemClipboardSafe(), onTextCopied);
    }

    public ClipboardMonitor(Clipboard clipboard, Consumer<String> onTextCopied) {
        this.clipboard = clipboard;
        this.onTextCopied = Objects.requireNonNull(onTextCopied, "onTextCopied callback must not be null");
    }

    private static Clipboard getSystemClipboardSafe() {
        try {
            return Toolkit.getDefaultToolkit().getSystemClipboard();
        } catch (HeadlessException e) {
            throw new IllegalStateException("AWT System Clipboard is not available in headless environment.", e);
        }
    }

    /**
     * Starts monitoring the clipboard.
     */
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            // Seed initial clipboard text to prevent false trigger on startup
            this.lastProcessedText = readClipboardTextSafe();
            if (clipboard != null) {
                clipboard.addFlavorListener(this);
            }
        }
    }

    /**
     * Stops monitoring the clipboard and cleans up listener registration.
     */
    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            if (clipboard != null) {
                try {
                    clipboard.removeFlavorListener(this);
                } catch (Exception ignored) {
                    // Ignore cleanup exceptions during shutdown
                }
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public String getLastProcessedText() {
        return lastProcessedText;
    }

    @Override
    public void flavorsChanged(FlavorEvent e) {
        if (!running.get()) {
            return;
        }
        processClipboardChange();
    }

    /**
     * Inspects current clipboard content and notifies callback if new text is detected.
     */
    public synchronized void processClipboardChange() {
        String currentText = readClipboardTextSafe();
        if (currentText != null && !currentText.equals(lastProcessedText)) {
            lastProcessedText = currentText;
            try {
                onTextCopied.accept(currentText);
            } catch (Exception ex) {
                System.err.println("Error processing clipboard text callback: " + ex.getMessage());
            }
        }
    }

    /**
     * Reads text safely from the clipboard with retry logic to handle temporary OS lock contention.
     */
    public String readClipboardTextSafe() {
        if (clipboard == null) {
            return null;
        }

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Transferable contents = clipboard.getContents(null);
                if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    Object data = contents.getTransferData(DataFlavor.stringFlavor);
                    if (data instanceof String text) {
                        return text;
                    }
                }
                return null;
            } catch (IllegalStateException e) {
                // Clipboard is locked by another Windows application; retry after brief sleep
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(75);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            } catch (UnsupportedFlavorException | IOException e) {
                // Non-text flavor or IO issue, safe to ignore
                return null;
            } catch (Exception e) {
                System.err.println("Unexpected error reading clipboard: " + e.getMessage());
                return null;
            }
        }
        return null;
    }
}
