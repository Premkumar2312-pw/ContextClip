package com.contextclip.agent;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.function.Consumer;

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
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            System.out.println("System tray is not supported or environment is headless. Operating in headless console mode.");
            this.traySupported = false;
            return false;
        }

        try {
            this.systemTray = SystemTray.getSystemTray();

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
                shutdown();
                onExit.run();
            });
            popup.add(exitItem);

            Image iconImage = createTrayImage(currentStatus);
            trayIcon = new TrayIcon(iconImage, "ContextClip Agent - " + currentStatus.getDisplayName(), popup);
            trayIcon.setImageAutoSize(true);

            systemTray.add(trayIcon);
            this.traySupported = true;
            System.out.println("ContextClip System Tray icon initialized successfully.");
            return true;
        } catch (AWTException e) {
            System.err.println("Failed to add tray icon to SystemTray: " + e.getMessage());
            this.traySupported = false;
            return false;
        } catch (Exception e) {
            System.err.println("Unexpected error initializing System Tray: " + e.getMessage());
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
        } catch (Exception e) {
            // Ignore UI update exceptions
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
            } catch (Exception ignored) {
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

    private static Image createTrayImage(ConnectionStatus status) {
        int size = 16;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Clipboard background
        g2d.setColor(new Color(30, 41, 59));
        g2d.fillRoundRect(1, 1, 14, 14, 3, 3);

        // Inner notepad
        g2d.setColor(Color.WHITE);
        g2d.fillRect(3, 4, 10, 10);

        // Status indicator dot in bottom right
        Color statusColor = switch (status) {
            case CONNECTED -> new Color(34, 197, 94);       // Green
            case PAUSED -> new Color(234, 179, 8);          // Amber/Yellow
            case RATE_LIMITED -> new Color(249, 115, 22);   // Orange
            case UNAUTHORIZED, FORBIDDEN -> new Color(239, 68, 68); // Red
            case SERVER_ERROR, DISCONNECTED -> new Color(148, 163, 184); // Gray/Slate
        };

        g2d.setColor(statusColor);
        g2d.fillOval(9, 9, 6, 6);
        g2d.setColor(Color.WHITE);
        g2d.drawOval(9, 9, 6, 6);

        g2d.dispose();
        return image;
    }
}
