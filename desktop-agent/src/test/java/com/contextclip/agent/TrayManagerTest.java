package com.contextclip.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class TrayManagerTest {

    private AtomicBoolean pauseToggleValue;
    private AtomicBoolean exitCalled;
    private TrayManager trayManager;

    @BeforeEach
    void setUp() {
        pauseToggleValue = new AtomicBoolean(false);
        exitCalled = new AtomicBoolean(false);
        trayManager = new TrayManager(pauseToggleValue::set, () -> exitCalled.set(true));
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (trayManager != null) {
            trayManager.shutdown();
        }
    }

    @Test
    void testInitialState() {
        assertFalse(trayManager.isPaused(), "Initial isPaused must be false");
        assertEquals(ConnectionStatus.CONNECTED, trayManager.getCurrentStatus(), "Initial status must be CONNECTED");
        assertEquals("Pause Monitoring", trayManager.getPauseResumeLabel());
    }

    @Test
    void testPauseStateTransition() {
        trayManager.setPausedState(true);

        assertTrue(trayManager.isPaused(), "isPaused must be true after pause");
        assertEquals(ConnectionStatus.PAUSED, trayManager.getCurrentStatus(), "Status must be PAUSED");
        assertEquals("Resume Monitoring", trayManager.getPauseResumeLabel(), "Menu label must be 'Resume Monitoring'");
    }

    @Test
    void testResumeStateTransition() {
        // Pause first
        trayManager.setPausedState(true);
        assertTrue(trayManager.isPaused());
        assertEquals(ConnectionStatus.PAUSED, trayManager.getCurrentStatus());

        // Now resume
        trayManager.setPausedState(false);
        assertFalse(trayManager.isPaused(), "isPaused must be false after resume");
        assertEquals(ConnectionStatus.CONNECTED, trayManager.getCurrentStatus(), "Status must return to CONNECTED");
        assertEquals("Pause Monitoring", trayManager.getPauseResumeLabel(), "Menu label must return to 'Pause Monitoring'");
    }

    @Test
    void testRepeatedPauseResumeCyclesRemainStable() {
        for (int i = 0; i < 5; i++) {
            trayManager.setPausedState(true);
            assertTrue(trayManager.isPaused());
            assertEquals(ConnectionStatus.PAUSED, trayManager.getCurrentStatus());
            assertEquals("Resume Monitoring", trayManager.getPauseResumeLabel());

            trayManager.setPausedState(false);
            assertFalse(trayManager.isPaused());
            assertEquals(ConnectionStatus.CONNECTED, trayManager.getCurrentStatus());
            assertEquals("Pause Monitoring", trayManager.getPauseResumeLabel());
        }

        // Final state verification
        assertFalse(trayManager.isPaused());
        assertEquals(ConnectionStatus.CONNECTED, trayManager.getCurrentStatus());
    }

    @Test
    void testCustomOperationalStatusRestoredOnResume() {
        // Suppose the agent was RATE_LIMITED before pausing
        trayManager.updateStatus(ConnectionStatus.RATE_LIMITED);
        assertEquals(ConnectionStatus.RATE_LIMITED, trayManager.getCurrentStatus());

        // Pause
        trayManager.setPausedState(true);
        assertEquals(ConnectionStatus.PAUSED, trayManager.getCurrentStatus());

        // Resume restores the previous operational status
        trayManager.setPausedState(false);
        assertEquals(ConnectionStatus.RATE_LIMITED, trayManager.getCurrentStatus());
    }

    @Test
    void testCreateTrayImageGeneratesValid32x32Image() {
        java.awt.Image image = TrayManager.createTrayImage(ConnectionStatus.CONNECTED);
        assertNotNull(image, "Tray image must not be null");
        assertEquals(32, image.getWidth(null), "Tray image width must be 32");
        assertEquals(32, image.getHeight(null), "Tray image height must be 32");
    }

    @Test
    void testCreateTrayImageHandlesAllConnectionStatuses() {
        for (ConnectionStatus status : ConnectionStatus.values()) {
            java.awt.Image img = TrayManager.createTrayImage(status);
            assertNotNull(img, "Image for status " + status + " must not be null");
            assertEquals(32, img.getWidth(null));
            assertEquals(32, img.getHeight(null));
        }
    }

    @Test
    void testAgentLoggerFunctionsSafelyWithoutExceptions() {
        assertDoesNotThrow(() -> {
            AgentLogger.info("Test info message");
            AgentLogger.debug("Test debug message");
            AgentLogger.warn("Test warn message");
            AgentLogger.error("Test error message", new RuntimeException("Test exception"));
            assertNotNull(AgentLogger.getLogFilePath());
        });
    }

    @Test
    void testInitializeSafeInAnyEnvironment() {
        // Must return true or false cleanly without throwing unhandled exceptions
        assertDoesNotThrow(() -> {
            boolean result = trayManager.initialize();
            // result is true in desktop UI, false in headless
            assertEquals(result, trayManager.isTraySupported());
        });
    }
}

