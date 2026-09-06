package com.contextclip.agent;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClipboardMonitorTest {

    private Clipboard testClipboard;
    private List<String> capturedContents;
    private ClipboardMonitor monitor;

    @BeforeEach
    void setUp() {
        testClipboard = new Clipboard("TestClipboard");
        capturedContents = new ArrayList<>();
        monitor = new ClipboardMonitor(testClipboard, capturedContents::add);
    }

    @Test
    void testStartAndStopLifecycle() {
        assertFalse(monitor.isRunning());
        monitor.start();
        assertTrue(monitor.isRunning());
        monitor.stop();
        assertFalse(monitor.isRunning());
    }

    @Test
    void testDetectsNewClipboardText() {
        monitor.start();
        testClipboard.setContents(new StringSelection("Hello ContextClip"), null);
        monitor.processClipboardChange();

        assertEquals(1, capturedContents.size());
        assertEquals("Hello ContextClip", capturedContents.get(0));
    }

    @Test
    void testAvoidsDuplicateConsecutiveText() {
        monitor.start();
        testClipboard.setContents(new StringSelection("SELECT * FROM employees;"), null);
        monitor.processClipboardChange();

        // Process again with same content
        monitor.processClipboardChange();

        assertEquals(1, capturedContents.size());
        assertEquals("SELECT * FROM employees;", capturedContents.get(0));
    }

    @Test
    void testDetectsSequenceOfDifferentTexts() {
        monitor.start();

        testClipboard.setContents(new StringSelection("docker compose up --build"), null);
        monitor.processClipboardChange();

        testClipboard.setContents(new StringSelection("git status"), null);
        monitor.processClipboardChange();

        assertEquals(2, capturedContents.size());
        assertEquals("docker compose up --build", capturedContents.get(0));
        assertEquals("git status", capturedContents.get(1));
    }

    @Test
    void testPausePreventsSendingClipboardContent() {
        monitor.start();
        monitor.pause();
        assertTrue(monitor.isPaused());

        testClipboard.setContents(new StringSelection("Should not be captured while paused"), null);
        monitor.processClipboardChange();

        assertEquals(0, capturedContents.size(), "No content should be captured while paused");
    }

    @Test
    void testResumeRestoresSendingClipboardContent() {
        monitor.start();
        monitor.pause();
        assertTrue(monitor.isPaused());

        testClipboard.setContents(new StringSelection("Ignored text"), null);
        monitor.processClipboardChange();
        assertEquals(0, capturedContents.size());

        monitor.resume();
        assertFalse(monitor.isPaused());

        testClipboard.setContents(new StringSelection("Captured after resume"), null);
        monitor.processClipboardChange();

        assertEquals(1, capturedContents.size());
        assertEquals("Captured after resume", capturedContents.get(0));
    }

    @Test
    void testResumeDoesNotAutomaticallyUploadOldPausedContent() {
        monitor.start();
        monitor.pause();
        assertTrue(monitor.isPaused());

        // Copy text while paused
        testClipboard.setContents(new StringSelection("PAUSE_SUPPRESSED_TEST"), null);
        monitor.processClipboardChange();
        assertEquals(0, capturedContents.size(), "Nothing captured while paused");

        // Resume monitoring
        monitor.resume();
        assertFalse(monitor.isPaused());

        // Merely resuming must not invoke callback or upload the old paused item
        assertEquals(0, capturedContents.size(), "Resuming must not automatically upload old paused content");

        // Only subsequent new content copied after resume triggers callback
        testClipboard.setContents(new StringSelection("RESUME_ACTIVE_TEST"), null);
        monitor.processClipboardChange();

        assertEquals(1, capturedContents.size());
        assertEquals("RESUME_ACTIVE_TEST", capturedContents.get(0));
    }

    @Test
    void testRepeatedPauseResumeCyclesAreStable() {
        monitor.start();

        for (int i = 1; i <= 3; i++) {
            monitor.pause();
            assertTrue(monitor.isPaused(), "Cycle " + i + " must be paused");

            testClipboard.setContents(new StringSelection("Suppressed " + i), null);
            monitor.processClipboardChange();
            assertEquals(i - 1, capturedContents.size());

            monitor.resume();
            assertFalse(monitor.isPaused(), "Cycle " + i + " must not be paused");

            testClipboard.setContents(new StringSelection("Active " + i), null);
            monitor.processClipboardChange();
            assertEquals(i, capturedContents.size());
            assertEquals("Active " + i, capturedContents.get(i - 1));
        }

        assertFalse(monitor.isPaused());
    }
}
