package com.contextclip.agent;

import java.awt.Graphics2D;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorEvent;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Monitors the system clipboard for text changes using a hybrid architecture:
 * 1. An AWT {@link FlavorListener} for immediate notification when clipboard flavors change.
 * 2. A lightweight background poll heartbeat (every 500ms) to catch same-flavor text copy events
 *    that Windows AWT FlavorListener omits (e.g. repeated Ctrl+C in VS Code or Notepad).
 * 3. Bounded backoff retry to handle transient OS clipboard locks from other Windows applications.
 */
public class ClipboardMonitor implements FlavorListener {

    private static final long POLL_INTERVAL_MS = 250;

    private final Clipboard clipboard;
    private final Consumer<String> onTextCopied;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private String lastProcessedText = null;
    private ScheduledExecutorService pollingExecutor;

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
            AgentLogger.warn("AWT System Clipboard is not available in headless environment: " + e.getMessage());
            return null;
        } catch (Throwable t) {
            AgentLogger.error("Failed to access AWT System Clipboard: " + t.getMessage(), t);
            return null;
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
            // Start background heartbeat to detect same-flavor text updates that Windows FlavorListener drops
            this.pollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "contextclip-clipboard-poll");
                t.setDaemon(true);
                return t;
            });
            this.pollingExecutor.scheduleWithFixedDelay(
                this::processClipboardChangeSafe,
                POLL_INTERVAL_MS,
                POLL_INTERVAL_MS,
                TimeUnit.MILLISECONDS
            );
        }
    }

    private void processClipboardChangeSafe() {
        try {
            processClipboardChange();
        } catch (Throwable t) {
            System.err.println("[ClipboardMonitor] Unexpected error in polling heartbeat: " + t.getMessage());
        }
    }

    /**
     * Pauses clipboard change notifications. Text copied while paused is not sent.
     */
    public synchronized void pause() {
        if (paused.compareAndSet(false, true)) {
            this.lastProcessedText = readClipboardTextSafe();
        }
    }

    /**
     * Resumes clipboard change notifications.
     */
    public synchronized void resume() {
        if (paused.compareAndSet(true, false)) {
            this.lastProcessedText = readClipboardTextSafe();
        }
    }

    public boolean isPaused() {
        return paused.get();
    }

    /**
     * Stops monitoring the clipboard and cleans up listener registration and heartbeat scheduler.
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
            if (pollingExecutor != null) {
                pollingExecutor.shutdownNow();
                pollingExecutor = null;
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
        processClipboardChangeSafe();
    }

    /**
     * Inspects current clipboard content and notifies callback if new text is detected.
     * Reading and duplicate state checking are synchronized, while callback dispatch
     * is executed outside the lock to prevent blocking clipboard detection.
     */
    public void processClipboardChange() {
        if (!running.get()) {
            return;
        }

        String textToEmit = null;
        synchronized (this) {
            if (!running.get()) {
                return;
            }
            try {
                String currentText = readClipboardTextSafe();
                if (currentText != null && !currentText.equals(lastProcessedText)) {
                    lastProcessedText = currentText;
                    if (!paused.get()) {
                        textToEmit = currentText;
                    }
                }
            } catch (Throwable t) {
                System.err.println("[ClipboardMonitor] Error reading clipboard text: " + t.getMessage());
            }
        }

        if (textToEmit != null) {
            try {
                onTextCopied.accept(textToEmit);
            } catch (Throwable ex) {
                System.err.println("[ClipboardMonitor] Error processing clipboard text callback: " + ex.getMessage());
            }
        }
    }

    /**
     * Reads clipboard content safely (image or text) with bounded backoff retry logic
     * to handle temporary OS lock contention by other Windows applications.
     * When an image is copied (e.g. screenshot or copied picture or image file),
     * it is converted to a PNG Base64 data URL ("data:image/png;base64,...").
     */
    public String readClipboardTextSafe() {
        if (clipboard == null) {
            return null;
        }

        int maxAttempts = 5;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Transferable contents = clipboard.getContents(null);
                if (contents != null) {
                    // 1. Check for image first - when an image/screenshot is copied,
                    // image flavor takes precedence even if the browser also attached an image URL or HTML.
                    Image image = extractImageFromTransferable(contents);
                    if (image != null) {
                        String dataUrl = convertImageToBase64DataUrl(image);
                        if (dataUrl != null && !dataUrl.isBlank()) {
                            return dataUrl;
                        }
                    }

                    // 2. Check for text
                    if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        Object data = contents.getTransferData(DataFlavor.stringFlavor);
                        if (data instanceof String text && !text.isBlank()) {
                            return text;
                        }
                    }
                }
                return null;
            } catch (IllegalStateException e) {
                // Clipboard is locked by another Windows application (e.g. VS Code, Excel, Chrome)
                // Retry safely using bounded backoff
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(attempt * 40L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            } catch (UnsupportedFlavorException | IOException e) {
                // Non-supported flavor or IO issue, safe to ignore
                return null;
            } catch (Exception e) {
                System.err.println("Unexpected error reading clipboard: " + e.getMessage());
                return null;
            }
        }
        return null;
    }

    private Image extractImageFromTransferable(Transferable contents) {
        if (contents == null) return null;

        // A. Standard AWT ImageFlavor (CF_DIB, CF_BITMAP, screenshots)
        try {
            if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Object data = contents.getTransferData(DataFlavor.imageFlavor);
                if (data instanceof Image img) {
                    return img;
                }
            }
        } catch (Throwable ignored) {}

        // B. Native image streams (e.g. image/png, image/jpeg, image/bmp from browsers or native apps)
        try {
            for (DataFlavor flavor : contents.getTransferDataFlavors()) {
                if (flavor != null && "image".equalsIgnoreCase(flavor.getPrimaryType())) {
                    try {
                        Object data = contents.getTransferData(flavor);
                        if (data instanceof Image img) {
                            return img;
                        } else if (data instanceof java.io.InputStream is) {
                            BufferedImage bi = javax.imageio.ImageIO.read(is);
                            if (bi != null) return bi;
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // C. Copied image file from Windows Explorer (e.g. .png, .jpg)
        try {
            if (contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                Object data = contents.getTransferData(DataFlavor.javaFileListFlavor);
                if (data instanceof List<?> list && list.size() == 1) {
                    Object first = list.get(0);
                    if (first instanceof File file && file.isFile()) {
                        String name = file.getName().toLowerCase();
                        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                                || name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp")) {
                            BufferedImage bi = javax.imageio.ImageIO.read(file);
                            if (bi != null) return bi;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private String convertImageToBase64DataUrl(Image image) {
        if (image == null) return null;
        try {
            // Guarantee full load of image dimensions and pixel data (fixes async ToolkitImage from Snipping Tool)
            Image loadedImage = new javax.swing.ImageIcon(image).getImage();
            int width = loadedImage.getWidth(null);
            int height = loadedImage.getHeight(null);
            if (width <= 0 || height <= 0) {
                return null;
            }

            // Downscale if insanely huge (e.g. > 2560px) to keep base64 within bounds and responsive
            double scale = 1.0;
            int maxDim = 2560;
            if (width > maxDim || height > maxDim) {
                scale = Math.min((double) maxDim / width, (double) maxDim / height);
            }
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));

            BufferedImage standardImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = standardImage.createGraphics();
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.drawImage(loadedImage, 0, 0, targetWidth, targetHeight, null);
            } finally {
                g2.dispose();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean written = javax.imageio.ImageIO.write(standardImage, "png", baos);
            if (!written || baos.size() == 0) {
                // Fallback to standard RGB in case ARGB encoding has issues with particular color models
                baos.reset();
                BufferedImage rgbImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2Rgb = rgbImage.createGraphics();
                try {
                    g2Rgb.drawImage(loadedImage, 0, 0, targetWidth, targetHeight, null);
                } finally {
                    g2Rgb.dispose();
                }
                javax.imageio.ImageIO.write(rgbImage, "png", baos);
            }

            byte[] bytes = baos.toByteArray();
            if (bytes.length == 0) {
                return null;
            }
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Throwable t) {
            AgentLogger.warn("Failed to convert clipboard image to PNG Base64: " + t.getMessage());
            return null;
        }
    }
}
