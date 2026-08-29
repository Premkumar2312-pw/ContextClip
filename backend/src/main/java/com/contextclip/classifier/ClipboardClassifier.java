package com.contextclip.classifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class ClipboardClassifier {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Regular Expression Patterns for Rule-Based Detection
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);

    private static final Pattern SQL_START_PATTERN = Pattern.compile(
            "^(SELECT|INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|CREATE\\s+TABLE|ALTER\\s+TABLE|DROP\\s+TABLE|CREATE\\s+DATABASE|TRUNCATE\\s+TABLE)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern JAVA_STACKTRACE_PATTERN = Pattern.compile(
            "(\\bat\\s+[a-zA-Z0-9_.$]+\\([a-zA-Z0-9_]+(\\.(java|kt|scala))?:\\d+\\)|Exception in thread|\\b[A-Za-z0-9_]+Exception\\b|\\b[A-Za-z0-9_]+Error\\b)"
    );

    private static final Pattern PYTHON_STACKTRACE_PATTERN = Pattern.compile(
            "(Traceback \\(most recent call last\\):|File \".+\", line \\d+)"
    );

    private static final Pattern DOCKER_CLI_PATTERN = Pattern.compile(
            "^(docker\\s+compose|docker-compose|docker)\\b.*", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GIT_CLI_PATTERN = Pattern.compile(
            "^git\\b.*", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern MAVEN_CLI_PATTERN = Pattern.compile(
            "^(\\./)?(mvn|mvnw)\\b.*", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JS_CLI_PATTERN = Pattern.compile(
            "^(npm|npx|yarn|pnpm)\\b.*", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PYTHON_CLI_PATTERN = Pattern.compile(
            "^(python|python3|pip|pip3|pytest)\\b.*", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern JAVA_CLI_PATTERN = Pattern.compile(
            "^(java|javac|gradle|\\./gradlew)\\b.*", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GENERAL_CLI_PATTERN = Pattern.compile(
            "^(cd|ls|dir|cat|echo|curl|wget|sudo|chmod|mkdir|rm|ps|kill|export|set|grep|find)\\b.*", Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DOCKERFILE_PATTERN = Pattern.compile(
            "(^|\\n)\\s*(FROM|RUN|ENV|EXPOSE|WORKDIR|CMD|ENTRYPOINT)\\s+.*", Pattern.CASE_INSENSITIVE
    );

    public ClassificationResult classify(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ClassificationResult("TEXT", "UNKNOWN", "GENERAL");
        }

        String trimmed = text.trim();

        // 1. JSON Detection
        if (isJson(trimmed)) {
            return new ClassificationResult("JSON", "UNKNOWN", "GENERAL");
        }

        // 2. URL Detection
        if (URL_PATTERN.matcher(trimmed).matches()) {
            return classifyUrl(trimmed);
        }

        // 3. Error / Stack Trace Detection
        ClassificationResult errorResult = checkError(trimmed);
        if (errorResult != null) {
            return errorResult;
        }

        // 4. SQL Detection
        if (isSql(trimmed)) {
            return new ClassificationResult("SQL", "SQL", "DATABASE");
        }

        // 5. Terminal Command Detection
        ClassificationResult terminalResult = checkTerminalCommand(trimmed);
        if (terminalResult != null) {
            return terminalResult;
        }

        // 6. Configuration Detection
        ClassificationResult configResult = checkConfiguration(trimmed);
        if (configResult != null) {
            return configResult;
        }

        // 7. Code Detection (React, Spring Boot, Java, Python, JavaScript)
        ClassificationResult codeResult = checkCode(trimmed);
        if (codeResult != null) {
            return codeResult;
        }

        // 8. Fallback to Plain Text
        return new ClassificationResult("TEXT", "UNKNOWN", "GENERAL");
    }

    private boolean isJson(String trimmed) {
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                objectMapper.readTree(trimmed);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    private ClassificationResult classifyUrl(String url) {
        String lower = url.toLowerCase();
        String tech = "UNKNOWN";
        if (lower.contains("spring.io") || lower.contains("spring-boot") || lower.contains("spring")) {
            tech = "SPRING_BOOT";
        } else if (lower.contains("react")) {
            tech = "REACT";
        } else if (lower.contains("python")) {
            tech = "PYTHON";
        } else if (lower.contains("docker")) {
            tech = "DOCKER";
        } else if (lower.contains("github.com") || lower.contains("git-scm.com")) {
            tech = "GIT";
        } else if (lower.contains("maven.apache.org")) {
            tech = "MAVEN";
        }
        return new ClassificationResult("URL", tech, "DOCUMENTATION");
    }

    private ClassificationResult checkError(String text) {
        boolean isJavaError = JAVA_STACKTRACE_PATTERN.matcher(text).find();
        boolean isPythonError = PYTHON_STACKTRACE_PATTERN.matcher(text).find();
        boolean isGeneralError = text.contains("FATAL:") || text.contains("Error:") || text.contains("FAILED:");

        if (isJavaError || isPythonError || isGeneralError) {
            String tech = "UNKNOWN";
            String lower = text.toLowerCase();
            if (isPythonError || lower.contains("python") || text.contains("Traceback")) {
                tech = "PYTHON";
            } else if (lower.contains("react")) {
                tech = "REACT";
            } else if (lower.contains("spring") || lower.contains("springframework")) {
                tech = "SPRING_BOOT";
            } else if (text.contains("SQLException") || text.contains("PSQLException")) {
                tech = "SQL";
            } else if (isJavaError || lower.contains("java") || text.contains(".java:")) {
                tech = "JAVA";
            } else if (lower.contains("docker")) {
                tech = "DOCKER";
            } else if (lower.contains("git")) {
                tech = "GIT";
            }
            return new ClassificationResult("ERROR", tech, "ERROR");
        }
        return null;
    }

    private boolean isSql(String text) {
        if (SQL_START_PATTERN.matcher(text).find()) {
            return true;
        }
        String upper = text.toUpperCase();
        return upper.contains("SELECT ") && upper.contains(" FROM ");
    }

    private ClassificationResult checkTerminalCommand(String text) {
        String firstLine = text.lines().findFirst().orElse("").trim();
        if (DOCKER_CLI_PATTERN.matcher(firstLine).matches()) {
            return new ClassificationResult("TERMINAL_COMMAND", "DOCKER", "DEVOPS");
        }
        if (GIT_CLI_PATTERN.matcher(firstLine).matches()) {
            return new ClassificationResult("TERMINAL_COMMAND", "GIT", "DEVOPS");
        }
        if (MAVEN_CLI_PATTERN.matcher(firstLine).matches()) {
            return new ClassificationResult("TERMINAL_COMMAND", "MAVEN", "DEVOPS");
        }
        if (JS_CLI_PATTERN.matcher(firstLine).matches()) {
            return new ClassificationResult("TERMINAL_COMMAND", "JAVASCRIPT", "WEB");
        }
        if (PYTHON_CLI_PATTERN.matcher(firstLine).matches()) {
            return new ClassificationResult("TERMINAL_COMMAND", "PYTHON", "PROGRAMMING");
        }
        if (JAVA_CLI_PATTERN.matcher(firstLine).matches()) {
            return new ClassificationResult("TERMINAL_COMMAND", "JAVA", "PROGRAMMING");
        }
        if (GENERAL_CLI_PATTERN.matcher(firstLine).matches()) {
            return new ClassificationResult("TERMINAL_COMMAND", "UNKNOWN", "DEVOPS");
        }
        return null;
    }

    private ClassificationResult checkConfiguration(String text) {
        if (DOCKERFILE_PATTERN.matcher(text).find()) {
            return new ClassificationResult("CONFIGURATION", "DOCKER", "CONFIGURATION");
        }
        if (text.contains("<project") && (text.contains("<groupId>") || text.contains("xmlns=\"http://maven.apache.org"))) {
            return new ClassificationResult("CONFIGURATION", "MAVEN", "CONFIGURATION");
        }
        if (text.contains("spring.datasource") || text.contains("spring.application.name") || text.contains("server.port")) {
            return new ClassificationResult("CONFIGURATION", "SPRING_BOOT", "CONFIGURATION");
        }
        if (text.lines().count() >= 2 && text.lines().allMatch(l -> l.trim().isEmpty() || l.trim().startsWith("#") || l.contains("=") || l.contains(": "))) {
            return new ClassificationResult("CONFIGURATION", "UNKNOWN", "CONFIGURATION");
        }
        return null;
    }

    private ClassificationResult checkCode(String text) {
        // React check
        boolean hasReactKeywords = text.contains("import React") || text.contains("useState(")
                || text.contains("useEffect(") || text.contains("useContext(") || text.contains("useMemo(")
                || text.contains("useCallback(") || text.contains("<button") || text.contains("<div")
                || text.contains("className=");

        if (hasReactKeywords) {
            return new ClassificationResult("CODE", "REACT", "WEB");
        }

        // Spring Boot check
        boolean hasSpringBootKeywords = text.contains("@SpringBootApplication") || text.contains("@RestController")
                || text.contains("@GetMapping") || text.contains("@PostMapping") || text.contains("@PutMapping")
                || text.contains("@DeleteMapping") || text.contains("@Autowired") || text.contains("@Service")
                || text.contains("@Repository") || text.contains("import org.springframework.");

        if (hasSpringBootKeywords) {
            return new ClassificationResult("CODE", "SPRING_BOOT", "PROGRAMMING");
        }

        // Java check
        boolean hasJavaKeywords = text.contains("public class ") || text.contains("private class ")
                || text.contains("public interface ") || text.contains("public enum ")
                || text.contains("public static void main") || text.contains("System.out.println")
                || text.contains("import java.") || text.contains("import jakarta.") || text.contains("import javax.")
                || text.contains("@Override") || text.contains("@Entity") || text.contains("@Table");

        if (hasJavaKeywords) {
            return new ClassificationResult("CODE", "JAVA", "PROGRAMMING");
        }

        // Python check
        boolean hasPythonKeywords = (text.contains("def ") && text.contains(":"))
                || (text.startsWith("from ") && text.contains("import "))
                || (text.contains("\nfrom ") && text.contains("import "))
                || (text.startsWith("import ") && !text.contains("from ") && !text.contains(";") && !text.contains("{"))
                || (text.contains("class ") && text.contains(":") && !text.contains("{") && !text.contains(";"))
                || (text.contains("print(") && !text.contains(";") && !text.contains("System.out"));

        if (hasPythonKeywords) {
            return new ClassificationResult("CODE", "PYTHON", "PROGRAMMING");
        }

        // JavaScript check
        boolean hasJsKeywords = text.contains("const ") || text.contains("let ") || text.contains("var ")
                || text.contains("function ") || text.contains("console.log(") || text.contains("=> {");

        if (hasJsKeywords && (text.contains(";") || text.contains("{") || text.contains("}"))) {
            return new ClassificationResult("CODE", "JAVASCRIPT", "WEB");
        }

        return null;
    }
}
