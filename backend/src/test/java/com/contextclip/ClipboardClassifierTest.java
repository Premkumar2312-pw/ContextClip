package com.contextclip;

import com.contextclip.classifier.ClassificationResult;
import com.contextclip.classifier.ClipboardClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClipboardClassifierTest {

    private ClipboardClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ClipboardClassifier();
    }

    @Test
    void testSqlClassification() {
        ClassificationResult result = classifier.classify("SELECT * FROM employees;");
        assertEquals("SQL", result.type());
        assertEquals("SQL", result.technology());
        assertEquals("DATABASE", result.category());

        ClassificationResult insertResult = classifier.classify("INSERT INTO users (name, email) VALUES ('Alice', 'alice@example.com');");
        assertEquals("SQL", insertResult.type());
        assertEquals("SQL", insertResult.technology());
        assertEquals("DATABASE", insertResult.category());
    }

    @Test
    void testGitCommandClassification() {
        ClassificationResult result = classifier.classify("git push origin main");
        assertEquals("TERMINAL_COMMAND", result.type());
        assertEquals("GIT", result.technology());
        assertEquals("DEVOPS", result.category());
    }

    @Test
    void testDockerCommandClassification() {
        ClassificationResult result = classifier.classify("docker compose up --build");
        assertEquals("TERMINAL_COMMAND", result.type());
        assertEquals("DOCKER", result.technology());
        assertEquals("DEVOPS", result.category());

        ClassificationResult runResult = classifier.classify("docker run -d -p 5432:5432 postgres:17-alpine");
        assertEquals("TERMINAL_COMMAND", runResult.type());
        assertEquals("DOCKER", runResult.technology());
        assertEquals("DEVOPS", runResult.category());
    }

    @Test
    void testMavenCommandClassification() {
        ClassificationResult result = classifier.classify("mvn clean test");
        assertEquals("TERMINAL_COMMAND", result.type());
        assertEquals("MAVEN", result.technology());
        assertEquals("DEVOPS", result.category());
    }

    @Test
    void testJavaCodeClassification() {
        ClassificationResult result = classifier.classify("""
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("Hello World");
                    }
                }
                """);
        assertEquals("CODE", result.type());
        assertEquals("JAVA", result.technology());
        assertEquals("PROGRAMMING", result.category());
    }

    @Test
    void testSpringBootCodeClassification() {
        ClassificationResult result = classifier.classify("""
                @RestController
                @RequestMapping("/api/test")
                public class TestController {
                    @GetMapping
                    public String test() {
                        return "ok";
                    }
                }
                """);
        assertEquals("CODE", result.type());
        assertEquals("SPRING_BOOT", result.technology());
        assertEquals("PROGRAMMING", result.category());
    }

    @Test
    void testPythonCodeClassification() {
        ClassificationResult result = classifier.classify("""
                def calculate_total(items):
                    return sum(item.price for item in items)
                """);
        assertEquals("CODE", result.type());
        assertEquals("PYTHON", result.technology());
        assertEquals("PROGRAMMING", result.category());
    }

    @Test
    void testReactCodeClassification() {
        ClassificationResult result = classifier.classify("""
                import React, { useState } from 'react';

                export function Counter() {
                    const [count, setCount] = useState(0);
                    return <button onClick={() => setCount(count + 1)}>{count}</button>;
                }
                """);
        assertEquals("CODE", result.type());
        assertEquals("REACT", result.technology());
        assertEquals("WEB", result.category());
    }

    @Test
    void testJsonClassification() {
        ClassificationResult result = classifier.classify("{\"name\":\"Prem\",\"age\":20}");
        assertEquals("JSON", result.type());
        assertEquals("UNKNOWN", result.technology());
        assertEquals("GENERAL", result.category());
    }

    @Test
    void testUrlClassification() {
        ClassificationResult result = classifier.classify("https://spring.io/projects/spring-boot");
        assertEquals("URL", result.type());
        assertEquals("SPRING_BOOT", result.technology());
        assertEquals("DOCUMENTATION", result.category());

        ClassificationResult generalUrl = classifier.classify("https://example.com/some/article");
        assertEquals("URL", generalUrl.type());
        assertEquals("UNKNOWN", generalUrl.technology());
        assertEquals("DOCUMENTATION", generalUrl.category());
    }

    @Test
    void testErrorClassification() {
        ClassificationResult result = classifier.classify("NullPointerException at com.example.Test.main(Test.java:10)");
        assertEquals("ERROR", result.type());
        assertEquals("JAVA", result.technology());
        assertEquals("ERROR", result.category());

        ClassificationResult pythonError = classifier.classify("""
                Traceback (most recent call last):
                  File "app.py", line 12, in <module>
                    main()
                ZeroDivisionError: division by zero
                """);
        assertEquals("ERROR", pythonError.type());
        assertEquals("PYTHON", pythonError.technology());
        assertEquals("ERROR", pythonError.category());
    }

    @Test
    void testPlainTextClassification() {
        ClassificationResult result = classifier.classify("random normal sentence");
        assertEquals("TEXT", result.type());
        assertEquals("UNKNOWN", result.technology());
        assertEquals("GENERAL", result.category());
    }

    @Test
    void testNullAndBlankSafety() {
        ClassificationResult nullResult = classifier.classify(null);
        assertEquals("TEXT", nullResult.type());
        assertEquals("UNKNOWN", nullResult.technology());
        assertEquals("GENERAL", nullResult.category());

        ClassificationResult emptyResult = classifier.classify("");
        assertEquals("TEXT", emptyResult.type());
        assertEquals("UNKNOWN", emptyResult.technology());
        assertEquals("GENERAL", emptyResult.category());

        ClassificationResult blankResult = classifier.classify("   \n\t  ");
        assertEquals("TEXT", blankResult.type());
        assertEquals("UNKNOWN", blankResult.technology());
        assertEquals("GENERAL", blankResult.category());
    }
}
