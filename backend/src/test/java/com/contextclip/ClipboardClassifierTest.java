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

    @Test
    void testNodeJsCommandsClassifiedSpecifically() {
        ClassificationResult nodeApp = classifier.classify("node app.js");
        assertEquals("COMMAND", nodeApp.type());
        assertTrue(nodeApp.technologies().contains("NODE_JS"));
        assertTrue(nodeApp.categories().contains("DEVOPS") || nodeApp.categories().contains("BUILD"));

        ClassificationResult nodeServer = classifier.classify("node server.js");
        assertEquals("COMMAND", nodeServer.type());
        assertTrue(nodeServer.technologies().contains("NODE_JS"));

        ClassificationResult npmInstall = classifier.classify("npm install express");
        assertEquals("COMMAND", npmInstall.type());
        assertTrue(npmInstall.technologies().contains("NODE_JS"));

        ClassificationResult npxVite = classifier.classify("npx vite");
        assertEquals("COMMAND", npxVite.type());
        assertTrue(npxVite.technologies().contains("NODE_JS"));
    }

    @Test
    void testPhoneNumberClassification() {
        ClassificationResult usPhone = classifier.classify("+1-555-123-4567");
        assertEquals("PHONE_NUMBER", usPhone.type());
        assertEquals("COMMUNICATION", usPhone.category());

        ClassificationResult intlPhone = classifier.classify("+91 98765 43210");
        assertEquals("PHONE_NUMBER", intlPhone.type());
        assertEquals("COMMUNICATION", intlPhone.category());

        ClassificationResult parenPhone = classifier.classify("(555) 123-4567");
        assertEquals("PHONE_NUMBER", parenPhone.type());
        assertEquals("COMMUNICATION", parenPhone.category());
    }

    @Test
    void testStandardFormatsRetained() {
        ClassificationResult email = classifier.classify("user@example.com");
        assertEquals("EMAIL", email.type());
        assertEquals("COMMUNICATION", email.category());

        ClassificationResult url = classifier.classify("https://example.com");
        assertEquals("URL", url.type());

        ClassificationResult docker = classifier.classify("docker ps");
        assertEquals("COMMAND", docker.type());
        assertTrue(docker.technologies().contains("DOCKER"));

        ClassificationResult git = classifier.classify("git status");
        assertEquals("COMMAND", git.type());
        assertTrue(git.technologies().contains("GIT"));

        ClassificationResult sql = classifier.classify("SELECT * FROM users;");
        assertEquals("SQL", sql.type());

        ClassificationResult json = classifier.classify("{\"name\":\"Prem\"}");
        assertEquals("JSON", json.type());
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
        ClassificationResult pyResult = classifier.classify(pythonSnippet);
        assertEquals("CODE", pyResult.type());
        assertEquals("PYTHON", pyResult.technology());
        assertTrue(pyResult.technologies().contains("PYTHON"));
        assertEquals("PROGRAMMING", pyResult.category());

        // 2. Java code with HashMap and put
        String javaSnippet = """
                HashMap<String, Integer> map = new HashMap<>();
                map.put("Java", 1);
                """;
        ClassificationResult javaResult = classifier.classify(javaSnippet);
        assertEquals("CODE", javaResult.type());
        assertEquals("JAVA", javaResult.technology());
        assertTrue(javaResult.technologies().contains("JAVA"));
        assertEquals("PROGRAMMING", javaResult.category());

        // 3. Multi-line Git commands
        String gitSnippet = """
                git status
                git add .
                git commit -m "test"
                """;
        ClassificationResult gitResult = classifier.classify(gitSnippet);
        assertEquals("COMMAND", gitResult.type());
        assertEquals("GIT", gitResult.technology());
        assertTrue(gitResult.technologies().contains("GIT"));
        assertEquals("DEVOPS", gitResult.category());

        // 4. Docker run command
        String dockerSnippet = "docker run -d -p 8080:80 --name my-web-server nginx";
        ClassificationResult dockerResult = classifier.classify(dockerSnippet);
        assertEquals("COMMAND", dockerResult.type());
        assertEquals("DOCKER", dockerResult.technology());
        assertTrue(dockerResult.technologies().contains("DOCKER"));
        assertEquals("DEVOPS", dockerResult.category());

        // 5. PowerShell Get-Process
        String psSnippet = "Get-Process java,javaw -ErrorAction SilentlyContinue";
        ClassificationResult psResult = classifier.classify(psSnippet);
        assertEquals("COMMAND", psResult.type());
        assertEquals("POWERSHELL", psResult.technology());
        assertTrue(psResult.technologies().contains("POWERSHELL"));
        assertEquals("DEVOPS", psResult.category());

        // 6. Phone number
        ClassificationResult phoneResult = classifier.classify("+91 96777 50232");
        assertEquals("PHONE_NUMBER", phoneResult.type());
        assertEquals("COMMUNICATION", phoneResult.category());

        // 7. Node.js commands
        ClassificationResult nodeApp = classifier.classify("node app.js");
        assertEquals("COMMAND", nodeApp.type());
        assertEquals("NODE_JS", nodeApp.technology());
        assertEquals("DEVOPS", nodeApp.category());

        ClassificationResult nodeWatch = classifier.classify("node --watch app.js");
        assertEquals("COMMAND", nodeWatch.type());
        assertEquals("NODE_JS", nodeWatch.technology());
        assertEquals("DEVOPS", nodeWatch.category());

        // 8. Email and URL
        ClassificationResult emailResult = classifier.classify("support@contextclip.com");
        assertEquals("EMAIL", emailResult.type());
        assertEquals("COMMUNICATION", emailResult.category());

        ClassificationResult urlResult = classifier.classify("https://github.com/contextclip/app");
        assertEquals("URL", urlResult.type());
    }

    @Test
    void testImageDetection() {
        ClassificationResult img = classifier.classify("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
        assertEquals("IMAGE", img.type());
        assertEquals("IMAGE", img.technology());
        assertEquals("IMAGE", img.category());
        assertFalse(img.sensitive());
    }

    // -------------------------------------------------------------------------
    // Phase 21 — Mathematical & Scientific Formulas
    // -------------------------------------------------------------------------

    @Test
    void testMathematicalFormulas() {
        // LaTeX Gamma distribution / calculus integral
        ClassificationResult gammaLatex = classifier.classify("\\int_0^\\infty x^{k-1}e^{-x}dx");
        assertEquals("FORMULA", gammaLatex.type());
        assertEquals("LATEX", gammaLatex.technology());
        assertEquals("MATHEMATICS", gammaLatex.category());
        assertTrue(gammaLatex.technologies().contains("CALCULUS"));
        assertTrue(gammaLatex.technologies().contains("STATISTICS"));

        // Unicode Math Gamma distribution
        ClassificationResult gammaUnicode = classifier.classify("∫₀∞ x^(k-1)e^(-x) dx");
        assertEquals("FORMULA", gammaUnicode.type());
        assertEquals("UNICODE_MATH", gammaUnicode.language());
        assertEquals("MATHEMATICS", gammaUnicode.category());
        assertTrue(gammaUnicode.technologies().contains("CALCULUS"));

        // Navier-Stokes equation (LaTeX)
        String navierStokes = "\\frac{\\partial \\mathbf{u}}{\\partial t} + (\\mathbf{u} \\cdot \\nabla) \\mathbf{u} = -\\frac{1}{\\rho}\\nabla p + \\nu \\nabla^2 \\mathbf{u}";
        ClassificationResult nsResult = classifier.classify(navierStokes);
        assertEquals("FORMULA", nsResult.type());
        assertEquals("MATHEMATICS", nsResult.category());
        assertTrue(nsResult.technologies().contains("PHYSICS"));
        assertTrue(nsResult.technologies().contains("CALCULUS"));
        assertTrue(nsResult.categories().contains("SCIENCE"));

        // Navier-Stokes equation (Unicode)
        String nsUnicode = "∂u/∂t + (u · ∇)u = -(1/ρ)∇p + ν∇²u";
        ClassificationResult nsUniResult = classifier.classify(nsUnicode);
        assertEquals("FORMULA", nsUniResult.type());
        assertEquals("MATHEMATICS", nsUniResult.category());
        assertTrue(nsUniResult.technologies().contains("PHYSICS"));

        // Schrödinger equation
        ClassificationResult schrodinger = classifier.classify("iℏ \\frac{\\partial}{\\partial t}\\Psi = \\hat{H}\\Psi");
        assertEquals("FORMULA", schrodinger.type());
        assertTrue(schrodinger.technologies().contains("PHYSICS"));
        assertEquals("MATHEMATICS", schrodinger.category());

        // Matrix
        ClassificationResult matrix = classifier.classify("\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}");
        assertEquals("FORMULA", matrix.type());
        assertEquals("LATEX", matrix.technology());
        assertEquals("MATHEMATICS", matrix.category());
    }

    // -------------------------------------------------------------------------
    // Phase 21 — Multilingual Natural Text
    // -------------------------------------------------------------------------

    @Test
    void testMultilingualDetection() {
        // Tamil
        ClassificationResult tamil = classifier.classify("வணக்கம், இது ContextClip சோதனை.");
        assertEquals("PLAIN_TEXT", tamil.type());
        assertEquals("TAMIL", tamil.technology());
        assertEquals("COMMUNICATION", tamil.category());

        // Hindi
        ClassificationResult hindi = classifier.classify("नमस्ते, यह ContextClip का परीक्षण है।");
        assertEquals("PLAIN_TEXT", hindi.type());
        assertEquals("HINDI", hindi.technology());
        assertEquals("COMMUNICATION", hindi.category());

        // Japanese
        ClassificationResult japanese = classifier.classify("こんにちは、ContextClipのテストです。");
        assertEquals("PLAIN_TEXT", japanese.type());
        assertEquals("JAPANESE", japanese.technology());
        assertEquals("COMMUNICATION", japanese.category());

        // Chinese
        ClassificationResult chinese = classifier.classify("你好，这是ContextClip的测试。");
        assertEquals("PLAIN_TEXT", chinese.type());
        assertEquals("CHINESE", chinese.technology());
        assertEquals("COMMUNICATION", chinese.category());

        // Arabic
        ClassificationResult arabic = classifier.classify("مرحبا، هذا اختبار ContextClip.");
        assertEquals("PLAIN_TEXT", arabic.type());
        assertEquals("ARABIC", arabic.technology());
        assertEquals("COMMUNICATION", arabic.category());

        // Russian
        ClassificationResult russian = classifier.classify("Привет, это тест ContextClip.");
        assertEquals("PLAIN_TEXT", russian.type());
        assertEquals("RUSSIAN", russian.technology());
        assertEquals("COMMUNICATION", russian.category());

        // French
        ClassificationResult french = classifier.classify("C'est un test pour la langue française avec des accents.");
        assertEquals("PLAIN_TEXT", french.type());
        assertEquals("FRENCH", french.technology());

        // German
        ClassificationResult german = classifier.classify("Das ist ein Test für die deutsche Sprache mit Umlauten.");
        assertEquals("PLAIN_TEXT", german.type());
        assertEquals("GERMAN", german.technology());

        // Spanish
        ClassificationResult spanish = classifier.classify("¡Hola! Este es un examen para el idioma español.");
        assertEquals("PLAIN_TEXT", spanish.type());
        assertEquals("SPANISH", spanish.technology());
    }

    // -------------------------------------------------------------------------
    // Phase 21 — Expanded Programming Languages
    // -------------------------------------------------------------------------

    @Test
    void testProgrammingLanguages() {
        // C
        String cCode = "#include <stdio.h>\nint main(int argc, char **argv) {\n    printf(\"Hello from C\\n\");\n    return 0;\n}";
        ClassificationResult cRes = classifier.classify(cCode);
        assertEquals("CODE", cRes.type());
        assertEquals("C", cRes.technology());
        assertEquals("PROGRAMMING", cRes.category());

        // C++
        String cppCode = "#include <iostream>\nint main() {\n    std::cout << \"Hello from C++\" << std::endl;\n    return 0;\n}";
        ClassificationResult cppRes = classifier.classify(cppCode);
        assertEquals("CODE", cppRes.type());
        assertEquals("CPP", cppRes.technology());
        assertEquals("PROGRAMMING", cppRes.category());

        // C#
        String csCode = "using System;\npublic class Program {\n    public static void Main() {\n        Console.WriteLine(\"Hello\");\n    }\n}";
        ClassificationResult csRes = classifier.classify(csCode);
        assertEquals("CODE", csRes.type());
        assertEquals("CSHARP", csRes.language());
        assertEquals("PROGRAMMING", csRes.category());

        // Go
        String goCode = "package main\nimport \"fmt\"\nfunc main() {\n    fmt.Println(\"Hello from Go\")\n}";
        ClassificationResult goRes = classifier.classify(goCode);
        assertEquals("CODE", goRes.type());
        assertEquals("GO", goRes.technology());
        assertEquals("PROGRAMMING", goRes.category());

        // Rust
        String rustCode = "fn main() {\n    let mut msg = \"Hello from Rust\";\n    println!(\"{}\", msg);\n}";
        ClassificationResult rustRes = classifier.classify(rustCode);
        assertEquals("CODE", rustRes.type());
        assertEquals("RUST", rustRes.technology());
        assertEquals("PROGRAMMING", rustRes.category());

        // Swift
        String swiftCode = "import SwiftUI\nstruct ContentView: View {\n    @State private var counter: Int = 0\n    var body: some View {\n        Text(\"Swift\")\n    }\n}";
        ClassificationResult swiftRes = classifier.classify(swiftCode);
        assertEquals("CODE", swiftRes.type());
        assertEquals("SWIFT", swiftRes.technology());
        assertEquals("PROGRAMMING", swiftRes.category());

        // Ruby
        String rubyCode = "def greet(name)\n  puts \"Hello, #{name}!\"\nend\ngreet('ContextClip')\n";
        ClassificationResult rubyRes = classifier.classify(rubyCode);
        assertEquals("CODE", rubyRes.type());
        assertEquals("RUBY", rubyRes.technology());
        assertEquals("PROGRAMMING", rubyRes.category());

        // Dart
        String dartCode = "import 'package:flutter/material.dart';\nvoid main() {\n  runApp(const MyApp());\n}";
        ClassificationResult dartRes = classifier.classify(dartCode);
        assertEquals("CODE", dartRes.type());
        assertEquals("DART", dartRes.technology());
        assertEquals("PROGRAMMING", dartRes.category());

        // Scala
        String scalaCode = "object App extends App {\n  case class User(name: String)\n  println(\"Hello Scala\")\n}";
        ClassificationResult scalaRes = classifier.classify(scalaCode);
        assertEquals("CODE", scalaRes.type());
        assertEquals("SCALA", scalaRes.technology());
        assertEquals("PROGRAMMING", scalaRes.category());

        // Lua
        String luaCode = "local function calculate(a, b)\n  if a > b then\n    return a\n  end\n  return b\nend\n";
        ClassificationResult luaRes = classifier.classify(luaCode);
        assertEquals("CODE", luaRes.type());
        assertEquals("LUA", luaRes.technology());
        assertEquals("PROGRAMMING", luaRes.category());
    }

    // -------------------------------------------------------------------------
    // Phase 21 — Expanded Commands
    // -------------------------------------------------------------------------

    @Test
    void testExpandedCommands() {
        // Cargo
        ClassificationResult cargo = classifier.classify("cargo build --release");
        assertEquals("COMMAND", cargo.type());
        assertTrue(cargo.technologies().contains("RUST"));

        // Go run
        ClassificationResult goRun = classifier.classify("go run main.go");
        assertEquals("COMMAND", goRun.type());
        assertTrue(goRun.technologies().contains("GO"));

        // Dotnet run
        ClassificationResult dotnet = classifier.classify("dotnet run --project MyApp");
        assertEquals("COMMAND", dotnet.type());
        assertTrue(dotnet.technologies().contains("DOTNET"));

        // Composer
        ClassificationResult composer = classifier.classify("composer install --no-dev");
        assertEquals("COMMAND", composer.type());
        assertTrue(composer.technologies().contains("PHP"));

        // Helm
        ClassificationResult helm = classifier.classify("helm install my-app ./charts/my-app");
        assertEquals("COMMAND", helm.type());
        assertEquals("KUBERNETES", helm.technology());

        // Apt-get
        ClassificationResult apt = classifier.classify("apt-get update && apt-get install -y curl");
        assertEquals("COMMAND", apt.type());

        // Windows ipconfig
        ClassificationResult ipconfig = classifier.classify("ipconfig /all");
        assertEquals("COMMAND", ipconfig.type());

        // Windows tasklist
        ClassificationResult tasklist = classifier.classify("tasklist /v");
        assertEquals("COMMAND", tasklist.type());
    }

    // -------------------------------------------------------------------------
    // Phase 21 — Expanded Errors & Exceptions
    // -------------------------------------------------------------------------

    @Test
    void testExpandedErrors() {
        // Python exception
        ClassificationResult pyErr = classifier.classify("ModuleNotFoundError: No module named 'pandas'");
        assertEquals("ERROR_MESSAGE", pyErr.type());
        assertEquals("PYTHON", pyErr.technology());
        assertEquals("DEBUG", pyErr.category());

        // Java arithmetic exception
        ClassificationResult javaErr = classifier.classify("Exception in thread \"main\" java.lang.ArithmeticException: / by zero");
        assertEquals("ERROR_MESSAGE", javaErr.type());
        assertEquals("JAVA", javaErr.technology());
        assertEquals("DEBUG", javaErr.category());

        // npm error
        ClassificationResult npmErr = classifier.classify("npm ERR! code ERESOLVE\nnpm ERR! ERESOLVE unable to resolve dependency tree");
        assertEquals("ERROR_MESSAGE", npmErr.type());
        assertEquals("NODE_JS", npmErr.technology());
        assertEquals("DEBUG", npmErr.category());

        // Git error
        ClassificationResult gitErr = classifier.classify("fatal: not a git repository (or any of the parent directories): .git");
        assertEquals("ERROR_MESSAGE", gitErr.type());
        assertEquals("GIT", gitErr.technology());
        assertEquals("DEBUG", gitErr.category());

        // Maven build error
        ClassificationResult mvnErr = classifier.classify("[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.11.0:compile");
        assertEquals("ERROR_MESSAGE", mvnErr.type());
        assertEquals("MAVEN", mvnErr.technology());
        assertEquals("DEBUG", mvnErr.category());

        // Rust error
        ClassificationResult rustErr = classifier.classify("error[E0425]: cannot find value 'x' in this scope");
        assertEquals("ERROR_MESSAGE", rustErr.type());
        assertEquals("RUST", rustErr.technology());
        assertEquals("DEBUG", rustErr.category());
    }

    // -------------------------------------------------------------------------
    // Phase 21 — Network & Identifiers
    // -------------------------------------------------------------------------

    @Test
    void testNetworkAndIdentifiers() {
        // IPv6
        ClassificationResult ipv6 = classifier.classify("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        assertEquals("IP_ADDRESS", ipv6.type());
        assertEquals("NETWORKING", ipv6.category());

        // MAC Address
        ClassificationResult mac = classifier.classify("00:1A:2B:3C:4D:5E");
        assertEquals("MAC_ADDRESS", mac.type());
        assertEquals("NETWORKING", mac.category());

        // SHA-256 Hash
        ClassificationResult hash = classifier.classify("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertEquals("HASH", hash.type());
        assertEquals("IDENTIFIER", hash.category());
    }

    // -------------------------------------------------------------------------
    // Phase 21 — False-Positive Guards
    // -------------------------------------------------------------------------

    @Test
    void testFalsePositiveGuards() {
        // Ordinary sentence with simple math words and plus sign
        ClassificationResult prose1 = classifier.classify("The meeting is at 5 + 5 = 10 minutes past noon.");
        assertEquals("PLAIN_TEXT", prose1.type());
        assertEquals("GENERAL", prose1.category());

        // Ordinary sentence
        ClassificationResult prose2 = classifier.classify("I have 10 apples and 20 oranges in my basket.");
        assertEquals("PLAIN_TEXT", prose2.type());
        assertEquals("GENERAL", prose2.category());

        // Sentence mentioning error without being an actual error
        ClassificationResult prose3 = classifier.classify("Please let me know if any error happens during setup.");
        assertEquals("PLAIN_TEXT", prose3.type());
    }
}
