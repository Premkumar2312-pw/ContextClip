package com.contextclip;

import com.contextclip.classifier.ClassificationResult;
import com.contextclip.classifier.ClipboardClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClipboardClassifierTest {

    private ClipboardClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ClipboardClassifier();
    }

    // -------------------------------------------------------------------------
    // Phase 19 — multi-label and language tests
    // -------------------------------------------------------------------------

    @Test
    void testSensitiveJwtDetected() {
        // Realistic JWT-shaped token
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9" +
                ".eyJzdWIiOiJ1c2VyMTIzIiwicm9sZSI6IkFETUlOIn0" +
                ".SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        ClassificationResult r = classifier.classify(jwt);
        assertTrue(r.sensitive(), "JWT token must be detected as sensitive");
    }

    @Test
    void testSensitiveApiKey() {
        ClassificationResult r = classifier.classify("api_key=sk-abc123secretvalue9999longkey");
        assertTrue(r.sensitive(), "api_key= pattern must be flagged as sensitive");
    }

    @Test
    void testSensitivePrivateKey() {
        String pem = "-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEAx...\n-----END RSA PRIVATE KEY-----";
        ClassificationResult r = classifier.classify(pem);
        assertTrue(r.sensitive(), "PEM private key must be flagged as sensitive");
    }

    @Test
    void testSensitivePassword() {
        ClassificationResult r = classifier.classify("password=mysecretpassword123");
        assertTrue(r.sensitive(), "password= pattern must be flagged as sensitive");
    }

    @Test
    void testNonSensitivePlainText() {
        ClassificationResult r = classifier.classify("Hello world, this is a normal sentence.");
        assertFalse(r.sensitive(), "Normal text must NOT be flagged as sensitive");
    }

    @Test
    void testSpringBootCodeHasJavaLanguageAndTechs() {
        ClassificationResult r = classifier.classify("""
                @RestController
                @RequestMapping("/api/test")
                public class TestController {
                    @GetMapping
                    public String test() {
                        return "ok";
                    }
                }
                """);
        assertEquals("CODE", r.type());
        // Primary technology must be SPRING_BOOT (or JAVA) — both are valid primary values
        assertTrue(r.technologies().contains("SPRING_BOOT"), "technologies must include SPRING_BOOT");
        assertTrue(r.technologies().contains("JAVA"), "technologies must include JAVA");
        assertEquals("JAVA", r.language(), "language must be JAVA for Spring Boot code");
        assertEquals("PROGRAMMING", r.category());
        assertFalse(r.sensitive());
    }

    @Test
    void testJavaCodeLanguage() {
        ClassificationResult r = classifier.classify("""
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("Hello World");
                    }
                }
                """);
        assertEquals("CODE", r.type());
        assertEquals("JAVA", r.language());
        assertTrue(r.technologies().contains("JAVA"));
        assertEquals("PROGRAMMING", r.category());
    }

    @Test
    void testPythonCodeLanguage() {
        ClassificationResult r = classifier.classify("""
                def calculate_total(items):
                    return sum(item.price for item in items)
                """);
        assertEquals("CODE", r.type());
        assertEquals("PYTHON", r.language());
        assertTrue(r.technologies().contains("PYTHON"));
        assertEquals("PROGRAMMING", r.category());
    }

    @Test
    void testJsonMultiLabelCategory() {
        ClassificationResult r = classifier.classify("{\"name\":\"Prem\",\"age\":20}");
        assertEquals("JSON", r.type());
        assertTrue(r.categories().contains("DATA"), "JSON must be in DATA category");
    }

    @Test
    void testUrlHasDocumentationCategory() {
        ClassificationResult r = classifier.classify("https://docs.spring.io/spring-boot/reference/");
        assertEquals("URL", r.type());
        assertTrue(r.categories().contains("DOCUMENTATION"));
    }

    @Test
    void testUuidDetection() {
        ClassificationResult r = classifier.classify("550e8400-e29b-41d4-a716-446655440000");
        assertEquals("UUID", r.type());
    }

    @Test
    void testEmailDetection() {
        ClassificationResult r = classifier.classify("user@example.com");
        assertEquals("EMAIL", r.type());
    }

    @Test
    void testIpAddressDetection() {
        ClassificationResult r = classifier.classify("192.168.1.1");
        assertEquals("IP_ADDRESS", r.type());
    }

    @Test
    void testFalsePositiveGuard_NormalProseNotCommand() {
        // Plain English mentioning Docker and Java must NOT become COMMAND or CODE
        ClassificationResult r = classifier.classify("I am learning Docker and Java today, it is very interesting.");
        assertEquals("PLAIN_TEXT", r.type(),
                "Normal prose mentioning tech names must be classified as PLAIN_TEXT, not COMMAND/CODE");
        assertFalse(r.sensitive());
    }

    @Test
    void testFalsePositiveGuard_DockerMentionInSentence() {
        ClassificationResult r = classifier.classify("We should use Docker in our project for containerization.");
        assertEquals("PLAIN_TEXT", r.type(),
                "Prose mentioning Docker must not be COMMAND");
    }

    @Test
    void testDockerCommandDetection() {
        ClassificationResult r = classifier.classify("docker compose up --build");
        assertEquals("COMMAND", r.type());
        assertTrue(r.technologies().contains("DOCKER"));
        assertEquals("DEVOPS", r.category());
    }

    @Test
    void testGitCommandDetection() {
        ClassificationResult r = classifier.classify("git push origin main");
        assertEquals("COMMAND", r.type());
        assertTrue(r.technologies().contains("GIT"));
        assertEquals("DEVOPS", r.category());
    }

    @Test
    void testMavenCommandDetection() {
        ClassificationResult r = classifier.classify("mvn clean test");
        assertEquals("COMMAND", r.type());
        assertTrue(r.technologies().contains("MAVEN"));
    }

    @Test
    void testSqlSelectDetection() {
        ClassificationResult r = classifier.classify("SELECT * FROM employees;");
        assertEquals("SQL", r.type());
        assertTrue(r.technologies().contains("SQL"));
        assertEquals("DATABASE", r.category());
    }

    @Test
    void testSqlInsertDetection() {
        ClassificationResult r = classifier.classify("INSERT INTO users (name, email) VALUES ('Alice', 'alice@example.com');");
        assertEquals("SQL", r.type());
        assertEquals("DATABASE", r.category());
    }

    @Test
    void testStackTraceDetection() {
        ClassificationResult r = classifier.classify(
                "NullPointerException at com.example.Test.main(Test.java:10)");
        assertEquals("STACK_TRACE", r.type());
        assertTrue(r.technologies().contains("JAVA"));
        assertEquals("DEBUG", r.category());
    }

    @Test
    void testPythonTracebackDetection() {
        ClassificationResult r = classifier.classify("""
                Traceback (most recent call last):
                  File "app.py", line 12, in <module>
                    main()
                ZeroDivisionError: division by zero
                """);
        assertEquals("STACK_TRACE", r.type());
        assertEquals("PYTHON", r.language());
        assertEquals("DEBUG", r.category());
    }

    @Test
    void testDockerfileDetection() {
        ClassificationResult r = classifier.classify("""
                FROM eclipse-temurin:25-jre
                WORKDIR /app
                COPY target/app.jar app.jar
                EXPOSE 8080
                ENTRYPOINT ["java", "-jar", "app.jar"]
                """);
        assertEquals("CONFIGURATION", r.type());
        assertTrue(r.technologies().contains("DOCKER"));
    }

    @Test
    void testNullAndBlankSafety() {
        ClassificationResult nullResult = classifier.classify(null);
        assertEquals("PLAIN_TEXT", nullResult.type());
        assertFalse(nullResult.sensitive());

        ClassificationResult emptyResult = classifier.classify("");
        assertEquals("PLAIN_TEXT", emptyResult.type());

        ClassificationResult blankResult = classifier.classify("   \n\t  ");
        assertEquals("PLAIN_TEXT", blankResult.type());
    }

    @Test
    void testConfidenceIsPositive() {
        ClassificationResult r = classifier.classify("SELECT id FROM users WHERE active = true;");
        assertTrue(r.confidence() > 0f && r.confidence() <= 1.0f,
                "Confidence must be between 0 and 1");
    }

    @Test
    void testTechnologiesListIsNeverNull() {
        ClassificationResult r = classifier.classify("some random plain text here");
        assertNotNull(r.technologies(), "technologies() must never be null");
        assertNotNull(r.categories(), "categories() must never be null");
    }

    // -------------------------------------------------------------------------
    // React code test (Technology = TYPESCRIPT or JAVASCRIPT, has REACT in techs)
    // -------------------------------------------------------------------------

    @Test
    void testReactCodeClassification() {
        ClassificationResult r = classifier.classify("""
                import React, { useState } from 'react';

                export function Counter() {
                    const [count, setCount] = useState(0);
                    return <button onClick={() => setCount(count + 1)}>{count}</button>;
                }
                """);
        assertEquals("CODE", r.type());
        assertTrue(r.technologies().contains("REACT"), "React component must have REACT in technologies");
        assertEquals("WEB", r.category());
    }

    @Test
    void testJavaCollectionsSnippet() {
        String snippet = "HashMap<String, Integer> map = new HashMap<>(); map.put(\"Java\", 1);";
        ClassificationResult r = classifier.classify(snippet);
        assertEquals("CODE", r.type());
        assertEquals("JAVA", r.technology());
        assertTrue(r.technologies().contains("JAVA"));
        assertEquals("PROGRAMMING", r.category());
    }
}
