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

    @Test
    void testNodeJsCommandsClassifiedSpecifically() {
        ContentClassifier.Result nodeApp = classifier.classify("node app.js");
        assertEquals("COMMAND", nodeApp.type);
        assertTrue(nodeApp.technologies.contains("NODE_JS"));

        ContentClassifier.Result nodeServer = classifier.classify("node server.js");
        assertEquals("COMMAND", nodeServer.type);
        assertTrue(nodeServer.technologies.contains("NODE_JS"));

        ContentClassifier.Result npmInstall = classifier.classify("npm install express");
        assertEquals("COMMAND", npmInstall.type);
        assertTrue(npmInstall.technologies.contains("NODE_JS"));
    }

    @Test
    void testPhoneNumberClassification() {
        ContentClassifier.Result phone = classifier.classify("+1-555-123-4567");
        assertEquals("PHONE_NUMBER", phone.type);
        assertEquals("COMMUNICATION", phone.category);

        ContentClassifier.Result intl = classifier.classify("+91 98765 43210");
        assertEquals("PHONE_NUMBER", intl.type);
        assertEquals("COMMUNICATION", intl.category);
    }

    @Test
    void testCliCommandsAndUrls() {
        ContentClassifier.Result git = classifier.classify("git status");
        assertEquals("COMMAND", git.type);
        assertTrue(git.technologies.contains("GIT"));

        ContentClassifier.Result docker = classifier.classify("docker ps");
        assertEquals("COMMAND", docker.type);
        assertTrue(docker.technologies.contains("DOCKER"));

        ContentClassifier.Result email = classifier.classify("user@example.com");
        assertEquals("EMAIL", email.type);
        assertEquals("COMMUNICATION", email.category);

        ContentClassifier.Result url = classifier.classify("https://example.com");
        assertEquals("URL", url.type);
    }

    @Test
    void testImageClassification() {
        ContentClassifier.Result img = classifier.classify("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
        assertEquals("IMAGE", img.type);
        assertEquals("IMAGE", img.technology);
        assertEquals("IMAGE", img.category);
        assertFalse(img.sensitive);
    }

    @Test
    void testUserRequestedTargetedExamples() {
        // 1. Python code with imports, dictionary, and pandas DataFrame
        String pythonSnippet = """
                import pandas as pd
                df = pd.read_csv('data.csv')
                data = {
                    'Name': ['Alice', 'Bob'],
                    'Age': [25, 30]
                }
                df = pd.DataFrame(data)
                """;
        ContentClassifier.Result pyResult = classifier.classify(pythonSnippet);
        assertEquals("CODE", pyResult.type);
        assertEquals("PYTHON", pyResult.technology);
        assertTrue(pyResult.technologies.contains("PYTHON"));
        assertEquals("PROGRAMMING", pyResult.category);

        // 2. Java code with HashMap and put
        String javaSnippet = """
                HashMap<String, Integer> map = new HashMap<>();
                map.put("Java", 1);
                """;
        ContentClassifier.Result javaResult = classifier.classify(javaSnippet);
        assertEquals("CODE", javaResult.type);
        assertEquals("JAVA", javaResult.technology);
        assertTrue(javaResult.technologies.contains("JAVA"));
        assertEquals("PROGRAMMING", javaResult.category);

        // 3. Multi-line Git commands
        String gitSnippet = """
                git status
                git add .
                git commit -m "test"
                """;
        ContentClassifier.Result gitResult = classifier.classify(gitSnippet);
        assertEquals("COMMAND", gitResult.type);
        assertEquals("GIT", gitResult.technology);
        assertTrue(gitResult.technologies.contains("GIT"));
        assertEquals("DEVOPS", gitResult.category);

        // 4. Docker run command
        String dockerSnippet = "docker run -d -p 8080:80 --name my-web-server nginx";
        ContentClassifier.Result dockerResult = classifier.classify(dockerSnippet);
        assertEquals("COMMAND", dockerResult.type);
        assertEquals("DOCKER", dockerResult.technology);
        assertTrue(dockerResult.technologies.contains("DOCKER"));
        assertEquals("DEVOPS", dockerResult.category);

        // 5. PowerShell Get-Process
        String psSnippet = "Get-Process java,javaw -ErrorAction SilentlyContinue";
        ContentClassifier.Result psResult = classifier.classify(psSnippet);
        assertEquals("COMMAND", psResult.type);
        assertEquals("POWERSHELL", psResult.technology);
        assertTrue(psResult.technologies.contains("POWERSHELL"));
        assertEquals("DEVOPS", psResult.category);

        // 6. Phone number
        ContentClassifier.Result phoneResult = classifier.classify("+91 96777 50232");
        assertEquals("PHONE_NUMBER", phoneResult.type);
        assertEquals("COMMUNICATION", phoneResult.category);

        // 7. Node.js commands
        ContentClassifier.Result nodeApp = classifier.classify("node app.js");
        assertEquals("COMMAND", nodeApp.type);
        assertEquals("NODE_JS", nodeApp.technology);
        assertEquals("DEVOPS", nodeApp.category);

        ContentClassifier.Result nodeWatch = classifier.classify("node --watch app.js");
        assertEquals("COMMAND", nodeWatch.type);
        assertEquals("NODE_JS", nodeWatch.technology);
        assertEquals("DEVOPS", nodeWatch.category);

        // 8. Email and URL
        ContentClassifier.Result emailResult = classifier.classify("support@contextclip.com");
        assertEquals("EMAIL", emailResult.type);
        assertEquals("COMMUNICATION", emailResult.category);

        ContentClassifier.Result urlResult = classifier.classify("https://github.com/contextclip/app");
        assertEquals("URL", urlResult.type);
    }
}
