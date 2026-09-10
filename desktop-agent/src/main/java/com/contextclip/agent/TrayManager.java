package com.contextclip.agent;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Objects;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * Manages the Windows System Tray icon, menu, and status reporting for the Desktop Agent.
 * Gracefully falls back to headless mode if SystemTray is not supported by the environment.
 */
public class TrayManager {

    private final Consumer<Boolean> onPauseToggle;
    private final Runnable onExit;

    private boolean traySupported = false;
    private SystemTray systemTray = null;
    private TrayIcon trayIcon = null;
    private MenuItem statusItem = null;
    private MenuItem pauseResumeItem = null;

    private volatile ConnectionStatus currentStatus = ConnectionStatus.CONNECTED;
    private volatile ConnectionStatus previousOperationalStatus = ConnectionStatus.CONNECTED;
    private volatile boolean isPaused = false;

    public TrayManager(Consumer<Boolean> onPauseToggle, Runnable onExit) {
        this.onPauseToggle = Objects.requireNonNull(onPauseToggle, "onPauseToggle callback must not be null");
        this.onExit = Objects.requireNonNull(onExit, "onExit callback must not be null");
    }

    public synchronized boolean initialize() {
        AgentLogger.info("Starting System Tray initialization...");

        boolean isHeadless = true;
        try {
            isHeadless = GraphicsEnvironment.isHeadless();
            AgentLogger.info("GraphicsEnvironment.isHeadless() = " + isHeadless);
        } catch (Throwable t) {
            AgentLogger.warn("Failed to determine GraphicsEnvironment headless status: " + t.getMessage());
        }

        boolean isSupported = false;
        try {
            isSupported = SystemTray.isSupported();
            AgentLogger.info("SystemTray.isSupported() = " + isSupported);
        } catch (Throwable t) {
            AgentLogger.error("Exception checking SystemTray.isSupported(): " + t.getMessage(), t);
        }

        if (isHeadless || !isSupported) {
            AgentLogger.warn("System tray is not supported or environment is headless (headless="
                    + isHeadless + ", supported=" + isSupported + "). Operating in headless console mode.");
            this.traySupported = false;
            return false;
        }

        try {
            this.systemTray = SystemTray.getSystemTray();
            try {
                Dimension size = systemTray.getTrayIconSize();
                AgentLogger.info("SystemTray acquired. Preferred tray icon size: " + size.width + "x" + size.height);
            } catch (Throwable ignored) {
            }

            PopupMenu popup = new PopupMenu();

            // Status display (disabled so it functions as a display header)
            statusItem = new MenuItem("Status: " + currentStatus.getDisplayName());
            statusItem.setEnabled(false);
            popup.add(statusItem);

            popup.addSeparator();

            // Pause / Resume toggle
            pauseResumeItem = new MenuItem("Pause Monitoring");
            pauseResumeItem.addActionListener(e -> {
                boolean newPaused = !isPaused;
                setPausedState(newPaused);
                onPauseToggle.accept(newPaused);
            });
            popup.add(pauseResumeItem);

            popup.addSeparator();

            // Exit action
            MenuItem exitItem = new MenuItem("Exit ContextClip Agent");
            exitItem.addActionListener(e -> {
                AgentLogger.info("User selected 'Exit ContextClip Agent' from System Tray menu.");
                shutdown();
                onExit.run();
            });
            popup.add(exitItem);

            Image iconImage = createTrayImage(currentStatus);
            AgentLogger.info("Created initial tray image with status: " + currentStatus.name()
                    + " (" + iconImage.getWidth(null) + "x" + iconImage.getHeight(null) + ")");

            trayIcon = new TrayIcon(iconImage, "ContextClip Agent - " + currentStatus.getDisplayName(), popup);
            trayIcon.setImageAutoSize(true);
            AgentLogger.info("TrayIcon created with imageAutoSize=true.");

            systemTray.add(trayIcon);
            this.traySupported = true;
            AgentLogger.info("ContextClip System Tray icon added to SystemTray successfully.");
            return true;
        } catch (AWTException e) {
            AgentLogger.error("AWTException adding icon to SystemTray: " + e.getMessage(), e);
            this.traySupported = false;
            return false;
        } catch (Throwable t) {
            AgentLogger.error("Unexpected error initializing System Tray: " + t.getMessage(), t);
            this.traySupported = false;
            return false;
        }
    }

    public synchronized void updateStatus(ConnectionStatus status) {
        if (status != ConnectionStatus.PAUSED) {
            this.previousOperationalStatus = status;
        }
        this.currentStatus = status;
        if (!traySupported || trayIcon == null) {
            return;
        }

        try {
            if (statusItem != null) {
                statusItem.setLabel("Status: " + status.getDisplayName());
            }
            trayIcon.setToolTip("ContextClip Agent - " + status.getDisplayName());
            trayIcon.setImage(createTrayImage(status));
            AgentLogger.debug("SystemTray status updated to: " + status.name());
        } catch (Throwable t) {
            AgentLogger.warn("Failed to update tray icon status: " + t.getMessage());
        }
    }

    public synchronized void setPausedState(boolean paused) {
        this.isPaused = paused;
        if (pauseResumeItem != null) {
            pauseResumeItem.setLabel(paused ? "Resume Monitoring" : "Pause Monitoring");
        }
        if (paused) {
            updateStatus(ConnectionStatus.PAUSED);
        } else {
            // When resumed, explicitly transition to CONNECTED (or previous non-paused operational status)
            ConnectionStatus restoreStatus = (previousOperationalStatus != null && previousOperationalStatus != ConnectionStatus.PAUSED)
                    ? previousOperationalStatus
                    : ConnectionStatus.CONNECTED;
            updateStatus(restoreStatus);
        }
    }

    public synchronized void shutdown() {
        if (traySupported && systemTray != null && trayIcon != null) {
            try {
                systemTray.remove(trayIcon);
                AgentLogger.info("System Tray icon removed successfully.");
            } catch (Throwable t) {
                AgentLogger.warn("Failed to remove tray icon during shutdown: " + t.getMessage());
            }
            trayIcon = null;
            traySupported = false;
        }
    }

    public boolean isTraySupported() {
        return traySupported;
    }

    public ConnectionStatus getCurrentStatus() {
        return currentStatus;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public String getPauseResumeLabel() {
        return pauseResumeItem != null ? pauseResumeItem.getLabel() : (isPaused ? "Resume Monitoring" : "Pause Monitoring");
    }

    public static Image createTrayImage(ConnectionStatus status) {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Try loading custom branded resource if bundled
        boolean resourceLoaded = false;
        try (InputStream stream = TrayManager.class.getResourceAsStream("/icons/contextclip-tray.png")) {
            if (stream != null) {
                BufferedImage customImg = ImageIO.read(stream);
                if (customImg != null) {
                    g2d.drawImage(customImg, 0, 0, size, size, null);
                    resourceLoaded = true;
                }
            }
        } catch (Throwable ignored) {
        }

        if (!resourceLoaded) {
            // High-DPI ContextClip procedural brand icon
            // 1. Dark navy clipboard board (slate-900)
            g2d.setColor(new Color(15, 23, 42));
            g2d.fillRoundRect(3, 4, 26, 26, 6, 6);

            // 2. Clipboard top clip holder
            g2d.setColor(new Color(14, 165, 233)); // sky-500 ContextClip brand blue
            g2d.fillRoundRect(10, 1, 12, 6, 3, 3);
            g2d.setColor(new Color(224, 242, 254)); // sky-100 highlight
            g2d.fillRect(12, 3, 8, 2);

            // 3. Inner paper notepad
            g2d.setColor(Color.WHITE);
            g2d.fillRoundRect(6, 9, 20, 19, 3, 3);

            // 4. Subtle snippet lines on the paper
            g2d.setColor(new Color(203, 213, 225)); // slate-300
            g2d.fillRect(9, 13, 14, 2);
            g2d.fillRect(9, 17, 10, 2);
            g2d.fillRect(9, 21, 7, 2);
        }

        // 5. Status indicator badge in bottom-right corner
        Color statusColor = switch (status) {
            case CONNECTED -> new Color(34, 197, 94);       // Green
            case PAUSED -> new Color(234, 179, 8);          // Amber/Yellow
            case RATE_LIMITED -> new Color(249, 115, 22);   // Orange
            case UNAUTHORIZED, FORBIDDEN -> new Color(239, 68, 68); // Red
            case SERVER_ERROR, DISCONNECTED -> new Color(148, 163, 184); // Gray/Slate
            case UNCONFIGURED -> new Color(59, 130, 246);   // Blue
        };

        // Outer white border ring for maximum contrast against any Windows taskbar theme
        g2d.setColor(Color.WHITE);
        g2d.fillOval(19, 19, 12, 12);

        // Core status color dot
        g2d.setColor(statusColor);
        g2d.fillOval(21, 21, 8, 8);

        g2d.dispose();
        return image;
    }
}
