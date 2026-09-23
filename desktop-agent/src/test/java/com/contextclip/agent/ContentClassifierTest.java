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

    // -------------------------------------------------------------------------
    // Phase 21 — Mathematical & Scientific Formulas
    // -------------------------------------------------------------------------

    @Test
    void testMathematicalFormulas() {
        // LaTeX Gamma distribution / calculus integral
        ContentClassifier.Result gammaLatex = classifier.classify("\\int_0^\\infty x^{k-1}e^{-x}dx");
        assertEquals("FORMULA", gammaLatex.type);
        assertEquals("LATEX", gammaLatex.technology);
        assertEquals("MATHEMATICS", gammaLatex.category);
        assertTrue(gammaLatex.technologies.contains("CALCULUS"));
        assertTrue(gammaLatex.technologies.contains("STATISTICS"));

        // Unicode Math Gamma distribution
        ContentClassifier.Result gammaUnicode = classifier.classify("∫₀∞ x^(k-1)e^(-x) dx");
        assertEquals("FORMULA", gammaUnicode.type);
        assertEquals("UNICODE_MATH", gammaUnicode.language);
        assertEquals("MATHEMATICS", gammaUnicode.category);
        assertTrue(gammaUnicode.technologies.contains("CALCULUS"));

        // Navier-Stokes equation (LaTeX)
        String navierStokes = "\\frac{\\partial \\mathbf{u}}{\\partial t} + (\\mathbf{u} \\cdot \\nabla) \\mathbf{u} = -\\frac{1}{\\rho}\\nabla p + \\nu \\nabla^2 \\mathbf{u}";
        ContentClassifier.Result nsResult = classifier.classify(navierStokes);
        assertEquals("FORMULA", nsResult.type);
        assertEquals("MATHEMATICS", nsResult.category);
        assertTrue(nsResult.technologies.contains("PHYSICS"));
        assertTrue(nsResult.technologies.contains("CALCULUS"));
        assertTrue(nsResult.categories.contains("SCIENCE"));

        // Navier-Stokes equation (Unicode)
        String nsUnicode = "∂u/∂t + (u · ∇)u = -(1/ρ)∇p + ν∇²u";
        ContentClassifier.Result nsUniResult = classifier.classify(nsUnicode);
        assertEquals("FORMULA", nsUniResult.type);
        assertEquals("MATHEMATICS", nsUniResult.category);
        assertTrue(nsUniResult.technologies.contains("PHYSICS"));

        // Schrödinger equation
        ContentClassifier.Result schrodinger = classifier.classify("iℏ \\frac{\\partial}{\\partial t}\\Psi = \\hat{H}\\Psi");
        assertEquals("FORMULA", schrodinger.type);
        assertTrue(schrodinger.technologies.contains("PHYSICS"));
        assertEquals("MATHEMATICS", schrodinger.category);

        // Matrix
        ContentClassifier.Result matrix = classifier.classify("\\begin{pmatrix} a & b \\\\ c & d \\end{pmatrix}");
        assertEquals("FORMULA", matrix.type);
        assertEquals("LATEX", matrix.technology);
        assertEquals("MATHEMATICS", matrix.category);
    }

    // -------------------------------------------------------------------------
    // Phase 21 — Multilingual Natural Text
    // -------------------------------------------------------------------------

    @Test
    void testMultilingualDetection() {
        // Tamil
        ContentClassifier.Result tamil = classifier.classify("வணக்கம், இது ContextClip சோதனை.");
        assertEquals("PLAIN_TEXT", tamil.type);
        assertEquals("TAMIL", tamil.technology);
        assertEquals("COMMUNICATION", tamil.category);

        // Hindi
        ContentClassifier.Result hindi = classifier.classify("नमस्ते, यह ContextClip का परीक्षण है।");
        assertEquals("PLAIN_TEXT", hindi.type);
        assertEquals("HINDI", hindi.technology);
        assertEquals("COMMUNICATION", hindi.category);

        // Japanese
        ContentClassifier.Result japanese = classifier.classify("こんにちは、ContextClipのテストです。");
        assertEquals("PLAIN_TEXT", japanese.type);
        assertEquals("JAPANESE", japanese.technology);
        assertEquals("COMMUNICATION", japanese.category);

        // Chinese
        ContentClassifier.Result chinese = classifier.classify("你好，这是ContextClip的测试。");
        assertEquals("PLAIN_TEXT", chinese.type);
        assertEquals("CHINESE", chinese.technology);
        assertEquals("COMMUNICATION", chinese.category);

        // Arabic
        ContentClassifier.Result arabic = classifier.classify("مرحبا، هذا اختبار ContextClip.");
        assertEquals("PLAIN_TEXT", arabic.type);
        assertEquals("ARABIC", arabic.technology);
        assertEquals("COMMUNICATION", arabic.category);

        // Russian
        ContentClassifier.Result russian = classifier.classify("Привет, это тест ContextClip.");
        assertEquals("PLAIN_TEXT", russian.type);
        assertEquals("RUSSIAN", russian.technology);
        assertEquals("COMMUNICATION", russian.category);

        // French
        ContentClassifier.Result french = classifier.classify("C'est un test pour la langue française avec des accents.");
        assertEquals("PLAIN_TEXT", french.type);
        assertEquals("FRENCH", french.technology);

        // German
        ContentClassifier.Result german = classifier.classify("Das ist ein Test für die deutsche Sprache mit Umlauten.");
        assertEquals("PLAIN_TEXT", german.type);
        assertEquals("GERMAN", german.technology);

        // Spanish
        ContentClassifier.Result spanish = classifier.classify("¡Hola! Este es un examen para el idioma español.");
        assertEquals("PLAIN_TEXT", spanish.type);
        assertEquals("SPANISH", spanish.technology);
    }

    // -------------------------------------------------------------------------
    // Phase 21 — Expanded Programming Languages
    // -------------------------------------------------------------------------

    @Test
    void testProgrammingLanguages() {
        // C
        String cCode = "#include <stdio.h>\nint main(int argc, char **argv) {\n    printf(\"Hello from C\\n\");\n    return 0;\n}";
        ContentClassifier.Result cRes = classifier.classify(cCode);
        assertEquals("CODE", cRes.type);
        assertEquals("C", cRes.technology);
        assertEquals("PROGRAMMING", cRes.category);

        // C++
        String cppCode = "#include <iostream>\nint main() {\n    std::cout << \"Hello from C++\" << std::endl;\n    return 0;\n}";
        ContentClassifier.Result cppRes = classifier.classify(cppCode);
        assertEquals("CODE", cppRes.type);
        assertEquals("CPP", cppRes.technology);
        assertEquals("PROGRAMMING", cppRes.category);

        // C#
        String csCode = "using System;\npublic class Program {\n    public static void Main() {\n        Console.WriteLine(\"Hello\");\n    }\n}";
        ContentClassifier.Result csRes = classifier.classify(csCode);
        assertEquals("CODE", csRes.type);
        assertEquals("CSHARP", csRes.language);
        assertEquals("PROGRAMMING", csRes.category);

        // Go
        String goCode = "package main\nimport \"fmt\"\nfunc main() {\n    fmt.Println(\"Hello from Go\")\n}";
        ContentClassifier.Result goRes = classifier.classify(goCode);
        assertEquals("CODE", goRes.type);
        assertEquals("GO", goRes.technology);
        assertEquals("PROGRAMMING", goRes.category);

        // Rust
        String rustCode = "fn main() {\n    let mut msg = \"Hello from Rust\";\n    println!(\"{}\", msg);\n}";
        ContentClassifier.Result rustRes = classifier.classify(rustCode);
        assertEquals("CODE", rustRes.type);
        assertEquals("RUST", rustRes.technology);
        assertEquals("PROGRAMMING", rustRes.category);

        // Swift
        String swiftCode = "import SwiftUI\nstruct ContentView: View {\n    @State private var counter: Int = 0\n    var body: some View {\n        Text(\"Swift\")\n    }\n}";
        ContentClassifier.Result swiftRes = classifier.classify(swiftCode);
        assertEquals("CODE", swiftRes.type);
        assertEquals("SWIFT", swiftRes.technology);
        assertEquals("PROGRAMMING", swiftRes.category);

        // Ruby
        String rubyCode = "def greet(name)\n  puts \"Hello, #{name}!\"\nend\ngreet('ContextClip')\n";
        ContentClassifier.Result rubyRes = classifier.classify(rubyCode);
        assertEquals("CODE", rubyRes.type);
        assertEquals("RUBY", rubyRes.technology);
        assertEquals("PROGRAMMING", rubyRes.category);

        // Dart
        String dartCode = "import 'package:flutter/material.dart';\nvoid main() {\n  runApp(const MyApp());\n}";
        ContentClassifier.Result dartRes = classifier.classify(dartCode);
        assertEquals("CODE", dartRes.type);
        assertEquals("DART", dartRes.technology);
        assertEquals("PROGRAMMING", dartRes.category);

        // Scala
        String scalaCode = "object App extends App {\n  case class User(name: String)\n  println(\"Hello Scala\")\n}";
        ContentClassifier.Result scalaRes = classifier.classify(scalaCode);
        assertEquals("CODE", scalaRes.type);
        assertEquals("SCALA", scalaRes.technology);
        assertEquals("PROGRAMMING", scalaRes.category);

        // Lua
        String luaCode = "local function calculate(a, b)\n  if a > b then\n    return a\n  end\n  return b\nend\n";
        ContentClassifier.Result luaRes = classifier.classify(luaCode);
        assertEquals("CODE", luaRes.type);
        assertEquals("LUA", luaRes.technology);
        assertEquals("PROGRAMMING", luaRes.category);
    }

    // -------------------------------------------------------------------------
    // Phase 21 — Expanded Commands
    // -------------------------------------------------------------------------

    @Test
    void testExpandedCommands() {
        // Cargo
        ContentClassifier.Result cargo = classifier.classify("cargo build --release");
        assertEquals("COMMAND", cargo.type);
        assertTrue(cargo.technologies.contains("RUST"));

        // Go run
        ContentClassifier.Result goRun = classifier.classify("go run main.go");
        assertEquals("COMMAND", goRun.type);
        assertTrue(goRun.technologies.contains("GO"));

        // Dotnet run
        ContentClassifier.Result dotnet = classifier.classify("dotnet run --project MyApp");
        assertEquals("COMMAND", dotnet.type);
        assertTrue(dotnet.technologies.contains("DOTNET"));

        // Composer
        ContentClassifier.Result composer = classifier.classify("composer install --no-dev");
        assertEquals("COMMAND", composer.type);
        assertTrue(composer.technologies.contains("PHP"));

        // Helm
        ContentClassifier.Result helm = classifier.classify("helm install my-app ./charts/my-app");
        assertEquals("COMMAND", helm.type);
        assertEquals("KUBERNETES", helm.technology);

        // Apt-get
        ContentClassifier.Result apt = classifier.classify("apt-get update && apt-get install -y curl");
        assertEquals("COMMAND", apt.type);

        // Windows ipconfig
        ContentClassifier.Result ipconfig = classifier.classify("ipconfig /all");
        assertEquals("COMMAND", ipconfig.type);

        // Windows tasklist
        ContentClassifier.Result tasklist = classifier.classify("tasklist /v");
        assertEquals("COMMAND", tasklist.type);
    }

    // -------------------------------------------------------------------------
    // Phase 21 — Expanded Errors & Exceptions
    // -------------------------------------------------------------------------

    @Test
    void testExpandedErrors() {
        // Python exception
        ContentClassifier.Result pyErr = classifier.classify("ModuleNotFoundError: No module named 'pandas'");
        assertEquals("ERROR_MESSAGE", pyErr.type);
        assertEquals("PYTHON", pyErr.technology);
        assertEquals("DEBUG", pyErr.category);

        // Java arithmetic exception
        ContentClassifier.Result javaErr = classifier.classify("Exception in thread \"main\" java.lang.ArithmeticException: / by zero");
        assertEquals("ERROR_MESSAGE", javaErr.type);
        assertEquals("JAVA", javaErr.technology);
        assertEquals("DEBUG", javaErr.category);

        // npm error
        ContentClassifier.Result npmErr = classifier.classify("npm ERR! code ERESOLVE\nnpm ERR! ERESOLVE unable to resolve dependency tree");
        assertEquals("ERROR_MESSAGE", npmErr.type);
        assertEquals("NODE_JS", npmErr.technology);
        assertEquals("DEBUG", npmErr.category);

        // Git error
        ContentClassifier.Result gitErr = classifier.classify("fatal: not a git repository (or any of the parent directories): .git");
        assertEquals("ERROR_MESSAGE", gitErr.type);
        assertEquals("GIT", gitErr.technology);
        assertEquals("DEBUG", gitErr.category);

        // Maven build error
        ContentClassifier.Result mvnErr = classifier.classify("[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.11.0:compile");
        assertEquals("ERROR_MESSAGE", mvnErr.type);
        assertEquals("MAVEN", mvnErr.technology);
        assertEquals("DEBUG", mvnErr.category);

        // Rust error
        ContentClassifier.Result rustErr = classifier.classify("error[E0425]: cannot find value 'x' in this scope");
        assertEquals("ERROR_MESSAGE", rustErr.type);
        assertEquals("RUST", rustErr.technology);
        assertEquals("DEBUG", rustErr.category);
    }

    // -------------------------------------------------------------------------
    // Phase 21 — Network & Identifiers
    // -------------------------------------------------------------------------

    @Test
    void testNetworkAndIdentifiers() {
        // IPv6
        ContentClassifier.Result ipv6 = classifier.classify("2001:0db8:85a3:0000:0000:8a2e:0370:7334");
        assertEquals("IP_ADDRESS", ipv6.type);
        assertEquals("NETWORKING", ipv6.category);

        // MAC Address
        ContentClassifier.Result mac = classifier.classify("00:1A:2B:3C:4D:5E");
        assertEquals("MAC_ADDRESS", mac.type);
        assertEquals("NETWORKING", mac.category);

        // SHA-256 Hash
        ContentClassifier.Result hash = classifier.classify("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        assertEquals("HASH", hash.type);
        assertEquals("IDENTIFIER", hash.category);
    }

    // -------------------------------------------------------------------------
    // Phase 21 — False-Positive Guards
    // -------------------------------------------------------------------------

    @Test
    void testFalsePositiveGuards() {
        // Ordinary sentence with simple math words and plus sign
        ContentClassifier.Result prose1 = classifier.classify("The meeting is at 5 + 5 = 10 minutes past noon.");
        assertEquals("PLAIN_TEXT", prose1.type);
        assertEquals("GENERAL", prose1.category);

        // Ordinary sentence
        ContentClassifier.Result prose2 = classifier.classify("I have 10 apples and 20 oranges in my basket.");
        assertEquals("PLAIN_TEXT", prose2.type);
        assertEquals("GENERAL", prose2.category);

        // Sentence mentioning error without being an actual error
        ContentClassifier.Result prose3 = classifier.classify("Please let me know if any error happens during setup.");
        assertEquals("PLAIN_TEXT", prose3.type);
    }
}
