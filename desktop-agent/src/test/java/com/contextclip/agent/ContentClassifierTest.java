package com.contextclip.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentClassifierTest {

    private ContentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ContentClassifier();
    }

    @Test
    void testJavaCollectionsSnippet() {
        String snippet = "HashMap<String, Integer> map = new HashMap<>(); map.put(\"Java\", 1);";
        ContentClassifier.Result r = classifier.classify(snippet);
        assertEquals("CODE", r.type);
        assertEquals("JAVA", r.technology);
        assertTrue(r.technologies.contains("JAVA"));
        assertEquals("PROGRAMMING", r.category);
    }

    @Test
    void testStandardJavaClass() {
        String code = "public class Main { public static void main(String[] args) { System.out.println(\"Hello\"); } }";
        ContentClassifier.Result r = classifier.classify(code);
        assertEquals("CODE", r.type);
        assertEquals("JAVA", r.technology);
    }

    @Test
    void testSensitiveDetection() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozGz...";
        ContentClassifier.Result r = classifier.classify(jwt);
        assertTrue(r.sensitive);
    }

    @Test
    void testPlainText() {
        String text = "Just some normal text about Java and Python without any code.";
        ContentClassifier.Result r = classifier.classify(text);
        assertEquals("PLAIN_TEXT", r.type);
        assertEquals("UNKNOWN", r.technology);
    }
}
