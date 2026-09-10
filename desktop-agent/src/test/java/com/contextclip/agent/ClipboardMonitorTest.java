package com.contextclip.agent;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClipboardMonitorTest {

    private Clipboard testClipboard;
    private List<String> capturedContents;
    private ClipboardMonitor monitor;

    @BeforeEach
    void setUp() {
        testClipboard = new Clipboard("TestClipboard");
        capturedContents = new CopyOnWriteArrayList<>();
        monitor = new ClipboardMonitor(testClipboard, capturedContents::add);
    }

    @AfterEach
    void tearDown() {
        if (monitor != null) {
            monitor.stop();
        }
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

    private void waitForCapturedCount(int expectedCount, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (capturedContents.size() < expectedCount && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
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
        waitForCapturedCount(1, 1000);

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
        waitForCapturedCount(1, 1000);

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

    @Test
    void testCapturesComplexMultilineCodeWithIndentationAndTabs() {
        monitor.start();
        String codeSnippet = """
            public class HelloWorld {
            \tpublic static void main(String[] args) {
            \t\tSystem.out.println("Hello, ContextClip!");
            \t}
            }
            """;
        testClipboard.setContents(new StringSelection(codeSnippet), null);
        monitor.processClipboardChange();

        assertEquals(1, capturedContents.size());
        assertEquals(codeSnippet, capturedContents.get(0));
    }

    @Test
    void testCapturesUnicodeEmojiAndMixedLanguages() {
        monitor.start();
        String mixedText = "English + தமிழ் + हिन्दी + 日本語 + 한국어 + 中文 + 🙂 🚀 + =SUM(A1:A10)";
        testClipboard.setContents(new StringSelection(mixedText), null);
        monitor.processClipboardChange();

        assertEquals(1, capturedContents.size());
        assertEquals(mixedText, capturedContents.get(0));
    }

    @Test
    void testCapturesFormattedSqlAndJsonExactly() {
        monitor.start();
        String sql = "SELECT id, name, salary\nFROM employees\nWHERE department = 'Engineering'\nORDER BY salary DESC;";
        testClipboard.setContents(new StringSelection(sql), null);
        monitor.processClipboardChange();

        String json = "{\n  \"name\": \"ContextClip\",\n  \"active\": true\n}";
        testClipboard.setContents(new StringSelection(json), null);
        monitor.processClipboardChange();

        assertEquals(2, capturedContents.size());
        assertEquals(sql, capturedContents.get(0));
        assertEquals(json, capturedContents.get(1));
    }

    @Test
    void testReadClipboardHandlesTransientLockContentionWithRetry() {
        // A mock clipboard that throws IllegalStateException twice then returns valid text
        final int[] attempts = {0};
        Clipboard mockClipboard = new Clipboard("LockContentionClipboard") {
            @Override
            public java.awt.datatransfer.Transferable getContents(Object requestor) {
                attempts[0]++;
                if (attempts[0] < 3) {
                    throw new IllegalStateException("Clipboard busy (locked by another application)");
                }
                return new StringSelection("Recovered after lock contention");
            }
        };

        ClipboardMonitor resilientMonitor = new ClipboardMonitor(mockClipboard, text -> {});
        String result = resilientMonitor.readClipboardTextSafe();

        assertEquals("Recovered after lock contention", result);
        assertTrue(attempts[0] >= 3, "Should have retried at least 3 times before succeeding");
    }

    @Test
    void testConsecutiveFiveCopiesSequence() {
        monitor.start();
        String[] sequence = {"TEST_A", "TEST_B", "TEST_C", "TEST_D", "TEST_E"};
        for (String item : sequence) {
            testClipboard.setContents(new StringSelection(item), null);
            monitor.processClipboardChange();
        }

        waitForCapturedCount(5, 1000);
        assertEquals(5, capturedContents.size());
        for (int i = 0; i < sequence.length; i++) {
            assertEquals(sequence[i], capturedContents.get(i));
        }
    }

    @Test
    void testAlternatingSequenceDetectsAllChanges() {
        monitor.start();
        String[] sequence = {"HELLO", "WORLD", "HELLO"};
        for (String item : sequence) {
            testClipboard.setContents(new StringSelection(item), null);
            monitor.processClipboardChange();
        }

        waitForCapturedCount(3, 1000);
        assertEquals(3, capturedContents.size());
        assertEquals("HELLO", capturedContents.get(0));
        assertEquals("WORLD", capturedContents.get(1));
        assertEquals("HELLO", capturedContents.get(2));
    }

    @Test
    void testIdenticalConsecutiveCopyIsSuppressed() {
        monitor.start();
        testClipboard.setContents(new StringSelection("DUPLICATE_TEST"), null);
        monitor.processClipboardChange();
        testClipboard.setContents(new StringSelection("DUPLICATE_TEST"), null);
        monitor.processClipboardChange();

        assertEquals(1, capturedContents.size());
        assertEquals("DUPLICATE_TEST", capturedContents.get(0));
    }
}
