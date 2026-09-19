package com.contextclip.agent;

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
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
    private CheckboxMenuItem startupItem = null;

    private volatile ConnectionStatus currentStatus = ConnectionStatus.CONNECTED;
    private volatile ConnectionStatus previousOperationalStatus = ConnectionStatus.CONNECTED;
    private volatile boolean isPaused = false;

    public TrayManager(Consumer<Boolean> onPauseToggle, Runnable onExit) {
        this(onPauseToggle, onExit, false);
    }

    public TrayManager(Consumer<Boolean> onPauseToggle, Runnable onExit, boolean initialPaused) {
        this.onPauseToggle = Objects.requireNonNull(onPauseToggle, "onPauseToggle callback must not be null");
        this.onExit = Objects.requireNonNull(onExit, "onExit callback must not be null");
        this.isPaused = initialPaused;
        if (initialPaused) {
            this.currentStatus = ConnectionStatus.PAUSED;
        }
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

            // Header title (informational, disabled)
            MenuItem titleItem = new MenuItem("ContextClip");
            titleItem.setEnabled(false);
            popup.add(titleItem);

            // Status display (informational, disabled)
            statusItem = new MenuItem("Status: " + currentStatus.getDisplayName());
            statusItem.setEnabled(false);
            popup.add(statusItem);

            popup.addSeparator();

            // User-controlled Windows startup toggle
            if (StartupManager.isWindows()) {
                startupItem = new CheckboxMenuItem("Start with Windows", StartupManager.isStartupEnabled());
                startupItem.addItemListener(e -> {
                    if (startupItem != null) {
                        boolean requested = startupItem.getState();
                        boolean success = StartupManager.setStartupEnabled(requested);
                        if (!success) {
                            startupItem.setState(StartupManager.isStartupEnabled());
                        }
                    }
                });
                popup.add(startupItem);
            }

            // Explicit Pause / Resume action control
            pauseResumeItem = new MenuItem(isPaused ? "Resume Monitoring" : "Pause Monitoring");
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
            if (pauseResumeItem != null) {
                pauseResumeItem.setLabel(isPaused ? "Resume Monitoring" : "Pause Monitoring");
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

    public String getMonitoringLabel() {
        return getPauseResumeLabel();
    }

    public String getStatusItemLabel() {
        return statusItem != null ? statusItem.getLabel() : ("Status: " + currentStatus.getDisplayName());
    }

    public CheckboxMenuItem getStartupItem() {
        return startupItem;
    }

    public boolean isStartupItemPresent() {
        return startupItem != null;
    }

    public void refreshStartupState() {
        if (startupItem != null && StartupManager.isWindows()) {
            startupItem.setState(StartupManager.isStartupEnabled());
        }
    }

    public static Image createTrayImage(ConnectionStatus status) {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // 1. High-contrast ContextClip brand blue clipboard board (#2563EB)
        // Outstanding visibility across dark, light, and custom Windows taskbar themes
        g2d.setColor(new Color(37, 99, 235));
        g2d.fillRoundRect(4, 4, 24, 25, 5, 5);

        // Subtle dark edge definition
        g2d.setColor(new Color(29, 78, 216));
        g2d.drawRoundRect(4, 4, 23, 24, 5, 5);

        // 2. Top metallic clip
        g2d.setColor(new Color(148, 163, 184)); // Slate-400
        g2d.fillRoundRect(10, 2, 12, 5, 3, 3);
        g2d.setColor(new Color(226, 232, 240)); // Slate-200 highlight
        g2d.fillRoundRect(12, 1, 8, 3, 2, 2);

        // 3. Crisp white paper notepad
        g2d.setColor(Color.WHITE);
        g2d.fillRoundRect(7, 9, 18, 17, 3, 3);

        // 4. Clean snippet preview lines on the paper
        g2d.setColor(new Color(147, 197, 253)); // Soft blue
        g2d.fillRoundRect(9, 13, 12, 2, 1, 1);
        g2d.fillRoundRect(9, 17, 9, 2, 1, 1);
        g2d.fillRoundRect(9, 21, 6, 2, 1, 1);

        // 5. Status indicator badge in bottom-right corner
        Color statusColor = switch (status) {
            case CONNECTED -> new Color(16, 185, 129);       // Vibrant emerald green
            case PAUSED -> new Color(245, 158, 11);          // Amber
            case RATE_LIMITED -> new Color(249, 115, 22);   // Orange
            case UNAUTHORIZED, FORBIDDEN -> new Color(239, 68, 68); // Red
            case SERVER_ERROR, DISCONNECTED -> new Color(148, 163, 184); // Slate
            case UNCONFIGURED -> new Color(59, 130, 246);   // Blue
        };

        // Outer white border ring for maximum contrast against taskbar and clipboard
        g2d.setColor(Color.WHITE);
        g2d.fillOval(18, 18, 12, 12);

        // Core status color dot
        g2d.setColor(statusColor);
        g2d.fillOval(20, 20, 8, 8);

        g2d.dispose();
        return image;
    }
}
