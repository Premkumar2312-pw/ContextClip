package com.contextclip.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
