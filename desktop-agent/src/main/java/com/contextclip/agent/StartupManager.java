package com.contextclip.agent;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages Windows automatic startup for the ContextClip Desktop Agent.
 * Allows the user to toggle automatic startup via the system tray menu.
 */
public class StartupManager {

    private static final String STARTUP_BAT_NAME = "ContextClipAgent.bat";
    private static final String DISABLED_BAT_NAME = "ContextClipAgent.bat.disabled";

    // Testing hook for isolated unit tests
    static volatile Path testStartupDirectory = null;

    public static void setTestStartupDirectory(Path dir) {
        testStartupDirectory = dir;
    }

    public static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

    public static Path getStartupDirectory() {
        if (testStartupDirectory != null) {
            return testStartupDirectory;
        }
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) {
            return null;
        }
        return Paths.get(appData, "Microsoft", "Windows", "Start Menu", "Programs", "Startup");
    }

    public static Path getStartupBatPath() {
        Path startupDir = getStartupDirectory();
        if (startupDir == null) {
            return null;
        }
        return startupDir.resolve(STARTUP_BAT_NAME);
    }

    public static Path getDisabledBatPath() {
        Path startupDir = getStartupDirectory();
        if (startupDir == null) {
            return null;
        }
        return startupDir.resolve(DISABLED_BAT_NAME);
    }

    /**
     * Checks if Windows automatic startup is currently enabled.
     * Reflects the real Windows startup configuration (presence of ContextClipAgent.bat).
     */
    public static boolean isStartupEnabled() {
        if (!isWindows()) {
            return false;
        }
        Path batPath = getStartupBatPath();
        return batPath != null && Files.isRegularFile(batPath);
    }

    /**
     * Enables or disables automatic startup with Windows.
     * When enabled (ON): creates ContextClipAgent.bat in the Windows Startup folder.
     * When disabled (OFF): deletes/removes ContextClipAgent.bat so Windows will NOT launch it.
     * Also updates the persisted preference in agent.properties.
     */
    public static boolean setStartupEnabled(boolean enabled) {
        if (!isWindows()) {
            return false;
        }
        Path batPath = getStartupBatPath();
        Path disabledPath = getDisabledBatPath();
        if (batPath == null) {
            return false;
        }

        try {
            if (enabled) {
                Files.createDirectories(batPath.getParent());

                // Clean up any stale .disabled file if present
                if (disabledPath != null && Files.exists(disabledPath)) {
                    try {
                        Files.delete(disabledPath);
                    } catch (Exception ignored) {
                    }
                }

                // If startup batch is already present and valid, persist preference and return true
                if (Files.isRegularFile(batPath)) {
                    AgentConfig.saveStartupEnabled(true);
                    return true;
                }

                // Generate startup batch pointing to the installed or local ContextClipLauncher.cmd
                String targetCmd = resolveLauncherCommand();
                String scriptContent = "@echo off\r\nstart \"\" \"" + targetCmd + "\"\r\n";
                Files.writeString(batPath, scriptContent);
                AgentConfig.saveStartupEnabled(true);
                AgentLogger.info("ContextClip Agent Windows startup enabled: " + batPath);
                return true;
            } else {
                // OFF: remove the startup batch file so Windows Startup folder is empty of ContextClip
                if (Files.exists(batPath)) {
                    Files.delete(batPath);
                }
                if (disabledPath != null && Files.exists(disabledPath)) {
                    try {
                        Files.delete(disabledPath);
                    } catch (Exception ignored) {
                    }
                }
                AgentConfig.saveStartupEnabled(false);
                AgentLogger.info("ContextClip Agent Windows startup disabled (startup batch removed).");
                return true;
            }
        } catch (IOException e) {
            AgentLogger.warn("Failed to update Windows startup configuration: " + e.getMessage());
            return false;
        }
    }

    /**
     * Resolves the proper launcher command for ContextClipAgent.bat.
     * Prefers ContextClipLauncher.cmd if found next to the jar or in standard installed directory,
     * otherwise falls back to javaw with current jar location.
     */
    static String resolveLauncherCommand() {
        // 1. Check next to current executing code/jar
        try {
            File codeLocation = new File(StartupManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File codeDir = codeLocation.isDirectory() ? codeLocation : codeLocation.getParentFile();
            if (codeDir != null) {
                File localLauncher = new File(codeDir, "ContextClipLauncher.cmd");
                if (localLauncher.exists()) {
                    return localLauncher.getAbsolutePath();
                }
            }
        } catch (Exception ignored) {
        }

        // 2. Check standard installed application directory: %LOCALAPPDATA%\Programs\ContextClip Desktop\ContextClipLauncher.cmd
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            File installedLauncher = Paths.get(localAppData, "Programs", "ContextClip Desktop", "ContextClipLauncher.cmd").toFile();
            if (installedLauncher.exists()) {
                return installedLauncher.getAbsolutePath();
            }
        }

        // 3. Fallback: javaw with jar location
        try {
            File jarFile = new File(StartupManager.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return "javaw -jar \"" + jarFile.getAbsolutePath() + "\"";
        } catch (Exception e) {
            return "javaw -jar desktop-agent.jar";
        }
    }
}
