package com.contextclip.classifier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic, rule-based multi-label classifier for clipboard content.
 *
 * <p>Classification priority (evaluated in order):
 * <ol>
 *   <li>Sensitivity check (JWT, API key, private key, password patterns)</li>
 *   <li>JSON</li>
 *   <li>XML</li>
 *   <li>URL</li>
 *   <li>Email</li>
 *   <li>UUID</li>
 *   <li>IP address</li>
 *   <li>File path</li>
 *   <li>Stack trace / error</li>
 *   <li>SQL</li>
 *   <li>Terminal command</li>
 *   <li>Configuration (Dockerfile, YAML, .properties, POM)</li>
 *   <li>Code (language-specific keyword sets with false-positive guard)</li>
 *   <li>CSV</li>
 *   <li>Markdown</li>
 *   <li>Plain text fallback</li>
 * </ol>
 *
 * <p>Normal English sentences that merely mention technology names (e.g. "I am learning
 * Docker today") are NOT classified as CODE or COMMAND unless concrete syntax markers
 * are present.
 */
@Component
public class ClipboardClassifier {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Sensitivity patterns
    // -------------------------------------------------------------------------

    private static final Pattern JWT_PATTERN = Pattern.compile(
            "eyJ[A-Za-z0-9_-]{4,}\\.eyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}");
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
            "(?i)(api[_-]?key|apikey|secret[_-]?key|access[_-]?token|auth[_-]?token)\\s*[=:]\\s*[\\S]{8,}");
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(?i)(password|passwd|pwd)\\s*[=:]\\s*[\\S]{4,}");

    // -------------------------------------------------------------------------
    // URL / Email / UUID / IP / File path
    // -------------------------------------------------------------------------

    private static final Pattern URL_PATTERN = Pattern.compile(
            "^https?://\\S+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)(:\\d{1,5})?$");
    private static final Pattern FILE_PATH_WINDOWS = Pattern.compile(
            "^[A-Za-z]:\\\\(\\S+\\\\)*\\S*$");
    private static final Pattern FILE_PATH_UNIX = Pattern.compile(
            "^(/[^/\\s]+)+/?$");

    // -------------------------------------------------------------------------
    // SQL
    // -------------------------------------------------------------------------

    private static final Pattern SQL_KEYWORD_START = Pattern.compile(
            "^\\s*(SELECT|INSERT\\s+INTO|UPDATE\\s+\\w+\\s+SET|DELETE\\s+FROM|CREATE\\s+(TABLE|DATABASE|INDEX|VIEW|PROCEDURE|FUNCTION)|ALTER\\s+TABLE|DROP\\s+(TABLE|DATABASE|INDEX)|TRUNCATE\\s+TABLE|MERGE\\s+INTO|WITH\\s+\\w+\\s+AS)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // -------------------------------------------------------------------------
    // Stack trace / error
    // -------------------------------------------------------------------------

    private static final Pattern JAVA_STACKTRACE = Pattern.compile(
            "\\bat\\s+[a-zA-Z0-9_.$]+\\([a-zA-Z0-9_]+(\\.(java|kt|scala))?:\\d+\\)");
    private static final Pattern PYTHON_TRACEBACK = Pattern.compile(
            "Traceback \\(most recent call last\\):");
    private static final Pattern EXCEPTION_PATTERN = Pattern.compile(
            "\\b[A-Za-z][A-Za-z0-9_$]*(Exception|Error)\\b.*:(.*\\n)?\\s+at\\s+");

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?(\\d{1,4}[-.\\s]?)?(\\(?\\d{2,4}\\)?[-.\\s]?)?[\\d.\\s-]{6,14}\\d$");

    // -------------------------------------------------------------------------
    // Terminal commands
    // -------------------------------------------------------------------------

    private static final Pattern DOCKER_CMD = Pattern.compile(
            "^(docker\\s+compose|docker-compose|docker|podman)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern GIT_CMD = Pattern.compile(
            "^git\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAVEN_CMD = Pattern.compile(
            "^(\\./)?(mvn|mvnw)\\s+.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern NODE_CMD = Pattern.compile(
            "^(node|nodejs|npm|npx|yarn|pnpm|bun|deno)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PYTHON_CMD = Pattern.compile(
            "^(python[23]?|py|pip[23]?|pytest|poetry|uv|pipenv|conda)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVA_CMD = Pattern.compile(
            "^(java|javac|javadoc|jar|gradle|\\./gradlew|gradlew)\\s+.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern KUBECTL_CMD = Pattern.compile(
            "^kubectl\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern AWS_CMD = Pattern.compile(
            "^aws\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHELL_CMD = Pattern.compile(
            "^(cd|ls|dir|cat|echo|curl|wget|sudo|chmod|mkdir|rm|ps|kill|export|set|grep|find|sed|awk|tail|head|ssh|scp|cp|mv|touch|ping|netstat|ifconfig|ip\\s|systemctl|service\\s|apt|yum|brew|choco)\\s.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SHELL_SHEBANG = Pattern.compile(
            "^#!\\s*/.*/(bash|sh|zsh|fish|python|ruby|perl|node)\\b");
    private static final Pattern POWERSHELL_CMD = Pattern.compile(
            "^(Get-|Set-|New-|Remove-|Invoke-|Start-|Stop-|Write-|Read-|Import-|Export-|Add-|Test-|Update-|Install-|Uninstall-)\\w+.*",
            Pattern.CASE_INSENSITIVE);

    // -------------------------------------------------------------------------
    // Configuration files
    // -------------------------------------------------------------------------

    private static final Pattern DOCKERFILE = Pattern.compile(
            "(^|\\n)\\s*(FROM|RUN|ENV|EXPOSE|WORKDIR|CMD|ENTRYPOINT|COPY|ADD|ARG|LABEL|VOLUME|USER|HEALTHCHECK|ONBUILD)\\s+\\S.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern YAML_STRUCTURE = Pattern.compile(
            "(^|\\n)[a-zA-Z_][a-zA-Z0-9_\\-]*:\\s*(\\S.*)?\\n");
    private static final Pattern PROPERTIES_FILE = Pattern.compile(
            "^([a-zA-Z][a-zA-Z0-9._-]*)\\s*=\\s*.+");
    private static final Pattern MAVEN_POM = Pattern.compile(
            "<project[^>]*>.*<(groupId|artifactId|version)>", Pattern.DOTALL);
    private static final Pattern GRADLE_BUILD = Pattern.compile(
            "(plugins\\s*\\{|dependencies\\s*\\{|repositories\\s*\\{|implementation\\s+['\"]|testImplementation\\s+['\"])");
    private static final Pattern KUBERNETES_YAML = Pattern.compile(
            "(^|\\n)(apiVersion|kind|metadata|spec):\\s+\\S");
    private static final Pattern DOCKER_COMPOSE_YAML = Pattern.compile(
            "(^|\\n)(services|volumes|networks):\\s*\\n");
    private static final Pattern GITHUB_ACTIONS_YAML = Pattern.compile(
            "(^|\\n)(on:|jobs:|steps:|uses:|runs-on:)");
    private static final Pattern SPRING_PROPS = Pattern.compile(
            "(spring\\.(application|datasource|jpa|security|boot)|server\\.port|logging\\.level)");

    // -------------------------------------------------------------------------
    // CSV
    // -------------------------------------------------------------------------
    private static final Pattern CSV_PATTERN = Pattern.compile(
            "^([^,\\n]+,){2,}[^,\\n]*(\\n([^,\\n]+,){2,}[^,\\n]*){1,}$");

    // -------------------------------------------------------------------------
    // Markdown
    // -------------------------------------------------------------------------
    private static final Pattern MARKDOWN_PATTERN = Pattern.compile(
            "(^#{1,6}\\s.+|^\\*\\*.+\\*\\*|^- .+|^\\d+\\. .+|^```|^>\\s.+)",
            Pattern.MULTILINE);

    // -------------------------------------------------------------------------
    // Main classify method
    // -------------------------------------------------------------------------

    public ClassificationResult classify(String text) {
        if (text == null || text.isBlank()) {
            return ClassificationResult.simple("PLAIN_TEXT", "UNKNOWN", "GENERAL");
        }
        String trimmed = text.trim();

        // 0. Image Data URL
        if (trimmed.startsWith("data:image/")) {
            return ClassificationResult.of("IMAGE", "IMAGE", List.of("IMAGE"), List.of("IMAGE"), false, 1.0f);
        }

        // 1. Sensitivity check — always run first
        boolean isSensitive = detectSensitive(trimmed);

        // 2. Phone number (high priority communication pattern)
        if (isPhoneNumber(trimmed)) {
            return ClassificationResult.of("PHONE_NUMBER", null, List.of(), List.of("COMMUNICATION"), isSensitive, 0.95f);
        }

        // 3. Email
        if (EMAIL_PATTERN.matcher(trimmed).matches()) {
            return ClassificationResult.of("EMAIL", null, List.of(), List.of("COMMUNICATION"), isSensitive, 1.0f);
        }

        // 4. URL
        if (URL_PATTERN.matcher(trimmed).matches()) {
            return classifyUrl(trimmed, isSensitive);
        }

        // 5. Terminal commands (first non-empty line must match — guards against prose mentioning a tool)
        ClassificationResult cmdResult = checkCommand(trimmed, isSensitive);
        if (cmdResult != null) return cmdResult;

        // 6. Stack trace / error
        ClassificationResult errResult = checkError(trimmed, isSensitive);
        if (errResult != null) return errResult;

        // 7. SQL
        if (isSql(trimmed)) {
            return ClassificationResult.of("SQL", "SQL", List.of("SQL"), List.of("DATABASE"), isSensitive, 0.98f);
        }

        // 8. Code (language-specific, with false-positive guard)
        ClassificationResult codeResult = checkCode(trimmed, isSensitive);
        if (codeResult != null) return codeResult;

        // 9. Configuration files
        ClassificationResult configResult = checkConfiguration(trimmed, isSensitive);
        if (configResult != null) return configResult;

        // 10. Structured Data (JSON / XML / UUID / IP / File Path / CSV)
        if (isJson(trimmed)) {
            List<String> techs = new ArrayList<>();
            if (trimmed.contains("\"token\"") || trimmed.contains("\"jwt\"") || trimmed.contains("\"access_token\"")) {
                techs.add("JWT");
            }
            if (trimmed.contains("\"spring\"") || trimmed.contains("\"application\"")) {
                techs.add("SPRING_BOOT");
            }
            return ClassificationResult.of("JSON", null, techs, List.of("DATA"), isSensitive, 1.0f);
        }

        if (isXml(trimmed)) {
            List<String> techs = new ArrayList<>();
            if (MAVEN_POM.matcher(trimmed).find()) techs.add("MAVEN");
            return ClassificationResult.of("XML", null, techs, List.of("DATA"), isSensitive, 1.0f);
        }

        if (UUID_PATTERN.matcher(trimmed).matches()) {
            return ClassificationResult.of("UUID", null, List.of(), List.of("IDENTIFIER"), isSensitive, 0.98f);
        }

        if (IPV4_PATTERN.matcher(trimmed).matches()) {
            return ClassificationResult.of("IP_ADDRESS", null, List.of(), List.of("NETWORKING"), isSensitive, 1.0f);
        }

        if (!trimmed.contains("\n") && (FILE_PATH_WINDOWS.matcher(trimmed).matches() || FILE_PATH_UNIX.matcher(trimmed).matches())) {
            return ClassificationResult.of("FILE_PATH", null, List.of(), List.of("FILESYSTEM"), isSensitive, 0.9f);
        }

        if (CSV_PATTERN.matcher(trimmed).matches()) {
            return ClassificationResult.of("CSV", null, List.of(), List.of("DATA"), isSensitive, 0.85f);
        }

        // 11. Markdown
        if (isMarkdown(trimmed)) {
            return ClassificationResult.of("MARKDOWN", null, List.of(), List.of("DOCUMENTATION"), isSensitive, 0.8f);
        }

        // 12. Sensitive fallback (if sensitive but no other type matched)
        if (isSensitive) {
            return ClassificationResult.sensitiveText("PLAIN_TEXT");
        }

        return ClassificationResult.simple("PLAIN_TEXT", "UNKNOWN", "GENERAL");
    }

    // -------------------------------------------------------------------------
    // Sensitivity detection
    // -------------------------------------------------------------------------

    private boolean detectSensitive(String text) {
        return JWT_PATTERN.matcher(text).find()
                || API_KEY_PATTERN.matcher(text).find()
                || PRIVATE_KEY_PATTERN.matcher(text).find()
                || PASSWORD_PATTERN.matcher(text).find();
    }

    // -------------------------------------------------------------------------
    // JSON / XML
    // -------------------------------------------------------------------------

    private boolean isJson(String t) {
        if ((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"))) {
            try {
                objectMapper.readTree(t);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    private boolean isXml(String t) {
        if (t.startsWith("<") && t.endsWith(">")) {
            try {
                javax.xml.parsers.DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder()
                        .parse(new org.xml.sax.InputSource(new java.io.StringReader(t)));
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // URL classification
    // -------------------------------------------------------------------------

    private ClassificationResult classifyUrl(String url, boolean sensitive) {
        String lower = url.toLowerCase();
        List<String> techs = new ArrayList<>();
        if (lower.contains("github.com") || lower.contains("git-scm.com")) techs.add("GIT");
        if (lower.contains("spring.io") || lower.contains("springframework")) techs.add("SPRING_BOOT");
        if (lower.contains("react")) techs.add("REACT");
        if (lower.contains("docker")) techs.add("DOCKER");
        if (lower.contains("kubernetes") || lower.contains("k8s.io")) techs.add("KUBERNETES");
        if (lower.contains("maven.apache.org")) techs.add("MAVEN");
        if (lower.contains("npmjs.com") || lower.contains("node.js")) techs.add("NODE_JS");
        if (lower.contains("aws.amazon.com") || lower.contains("amazonaws.com")) techs.add("AWS");
        if (lower.contains("postgresql.org") || lower.contains("postgres")) techs.add("POSTGRESQL");
        if (lower.contains("mongodb")) techs.add("MONGODB");
        return ClassificationResult.of("URL", null, techs, List.of("DOCUMENTATION"), sensitive, 1.0f);
    }

    // -------------------------------------------------------------------------
    // Error / Stack trace
    // -------------------------------------------------------------------------

    private ClassificationResult checkError(String text, boolean sensitive) {
        boolean isJavaTrace   = JAVA_STACKTRACE.matcher(text).find();
        boolean isPyTrace     = PYTHON_TRACEBACK.matcher(text).find();
        boolean isExcPattern  = EXCEPTION_PATTERN.matcher(text).find();
        boolean hasErrorLabel = text.contains("FATAL:") || text.contains("ERROR:") || text.contains("FAILED:");

        if (!isJavaTrace && !isPyTrace && !isExcPattern && !hasErrorLabel) return null;

        List<String> techs = new ArrayList<>();
        String language = null;
        String lower = text.toLowerCase();

        if (isPyTrace || lower.contains("traceback") || text.contains(".py\"")) {
            techs.add("PYTHON"); language = "PYTHON";
        } else if (isJavaTrace || lower.contains("springframework") || text.contains(".java:")) {
            if (lower.contains("springframework") || lower.contains("spring")) techs.add("SPRING_BOOT");
            techs.add("JAVA"); language = "JAVA";
        } else if (lower.contains("react") || lower.contains(".tsx:") || lower.contains(".jsx:")) {
            techs.add("REACT"); language = "TYPESCRIPT";
        } else if (lower.contains("node") || lower.contains(".js:")) {
            techs.add("NODE_JS"); language = "JAVASCRIPT";
        } else if (lower.contains("docker")) {
            techs.add("DOCKER");
        }

        if (text.contains("PSQLException") || text.contains("SQLException")) techs.add("POSTGRESQL");

        String type = (isJavaTrace || isPyTrace || isExcPattern) ? "STACK_TRACE" : "ERROR_MESSAGE";
        return ClassificationResult.of(type, language, techs, List.of("DEBUG"), sensitive, 0.95f);
    }

    // -------------------------------------------------------------------------
    // SQL
    // -------------------------------------------------------------------------

    private boolean isSql(String text) {
        if (SQL_KEYWORD_START.matcher(text).find()) return true;
        String upper = text.toUpperCase();
        return upper.contains("SELECT ") && upper.contains(" FROM ")
                && (upper.contains(" WHERE ") || upper.contains(";") || upper.contains("\n"));
    }

    // -------------------------------------------------------------------------
    // Terminal / CLI commands
    // -------------------------------------------------------------------------

    private ClassificationResult checkCommand(String text, boolean sensitive) {
        // Only look at first non-blank line for command detection.
        // This is the false-positive guard: if the first line doesn't look like a command,
        // we don't classify the whole block as a command.
        String firstLine = text.lines().filter(l -> !l.isBlank()).findFirst().orElse("").trim();

        if (SHELL_SHEBANG.matcher(text).find()) {
            String lang = detectScriptLanguage(text);
            return ClassificationResult.of("COMMAND", lang, List.of(), List.of("DEVOPS"), sensitive, 0.9f);
        }
        if (DOCKER_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("DOCKER"), List.of("DEVOPS"), sensitive, 0.97f);
        }
        if (GIT_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("GIT"), List.of("DEVOPS"), sensitive, 0.97f);
        }
        if (KUBECTL_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("KUBERNETES"), List.of("DEVOPS"), sensitive, 0.97f);
        }
        if (AWS_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("AWS"), List.of("DEVOPS"), sensitive, 0.97f);
        }
        if (MAVEN_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("MAVEN"), List.of("DEVOPS", "BUILD"), sensitive, 0.97f);
        }
        if (NODE_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("NODE_JS"), List.of("DEVOPS", "BUILD"), sensitive, 0.97f);
        }
        if (PYTHON_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "PYTHON", List.of("PYTHON"), List.of("DEVOPS"), sensitive, 0.97f);
        }
        if (JAVA_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("JAVA"), List.of("BUILD"), sensitive, 0.97f);
        }
        if (POWERSHELL_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "POWERSHELL", List.of("POWERSHELL"), List.of("DEVOPS"), sensitive, 0.9f);
        }
        if (SHELL_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("SHELL"), List.of("DEVOPS"), sensitive, 0.9f);
        }
        return null;
    }

    private boolean isPhoneNumber(String text) {
        if (text.contains("\n") || text.length() < 7 || text.length() > 25) {
            return false;
        }
        if (IPV4_PATTERN.matcher(text).matches()) {
            return false;
        }
        long digits = text.chars().filter(Character::isDigit).count();
        return digits >= 7 && digits <= 15 && PHONE_PATTERN.matcher(text).matches();
    }

    private String detectScriptLanguage(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("python")) return "PYTHON";
        if (lower.contains("node")) return "JAVASCRIPT";
        if (lower.contains("ruby")) return "RUBY";
        if (lower.contains("perl")) return "PERL";
        return "SHELL";
    }

    // -------------------------------------------------------------------------
    // Configuration files
    // -------------------------------------------------------------------------

    private ClassificationResult checkConfiguration(String text, boolean sensitive) {
        List<String> techs = new ArrayList<>();
        List<String> cats  = new ArrayList<>();

        if (DOCKER_COMPOSE_YAML.matcher(text).find()) {
            techs.add("DOCKER"); cats.add("DEVOPS"); cats.add("CONFIGURATION");
            return ClassificationResult.of("CONFIGURATION", "YAML", techs, cats, sensitive, 0.95f);
        }
        if (KUBERNETES_YAML.matcher(text).find() && text.contains("apiVersion:")) {
            techs.add("KUBERNETES"); cats.add("DEVOPS"); cats.add("CONFIGURATION");
            return ClassificationResult.of("CONFIGURATION", "YAML", techs, cats, sensitive, 0.95f);
        }
        if (GITHUB_ACTIONS_YAML.matcher(text).find()) {
            techs.add("GITHUB_ACTIONS"); cats.add("DEVOPS");
            return ClassificationResult.of("CONFIGURATION", "YAML", techs, cats, sensitive, 0.9f);
        }
        if (DOCKERFILE.matcher(text).find()) {
            techs.add("DOCKER"); cats.add("DEVOPS");
            return ClassificationResult.of("CONFIGURATION", null, techs, cats, sensitive, 0.95f);
        }
        if (MAVEN_POM.matcher(text).find()) {
            techs.add("MAVEN"); cats.add("BUILD");
            return ClassificationResult.of("CONFIGURATION", "XML", techs, cats, sensitive, 0.95f);
        }
        if (GRADLE_BUILD.matcher(text).find()) {
            techs.add("GRADLE"); cats.add("BUILD");
            return ClassificationResult.of("CONFIGURATION", "GROOVY", techs, cats, sensitive, 0.9f);
        }
        if (SPRING_PROPS.matcher(text).find()) {
            techs.add("SPRING_BOOT"); cats.add("CONFIGURATION");
            String lang = text.contains(":") && YAML_STRUCTURE.matcher(text).find() ? "YAML" : "PROPERTIES";
            return ClassificationResult.of("CONFIGURATION", lang, techs, cats, sensitive, 0.93f);
        }
        // Generic YAML: must have multiple key: value lines
        long yamlLines = text.lines().filter(l -> l.matches("^[a-zA-Z_][a-zA-Z0-9_\\-]*:\\s*.*")).count();
        if (yamlLines >= 3 && text.contains(":\n") || (yamlLines >= 3 && text.contains(": "))) {
            cats.add("CONFIGURATION");
            return ClassificationResult.of("YAML", "YAML", techs, cats, sensitive, 0.75f);
        }
        // Generic .properties: all lines are comment or key=value
        long totalLines = text.lines().filter(l -> !l.isBlank()).count();
        long propLines  = text.lines().filter(l -> l.isBlank() || l.trim().startsWith("#") || PROPERTIES_FILE.matcher(l.trim()).matches()).count();
        if (totalLines >= 2 && propLines == totalLines) {
            cats.add("CONFIGURATION");
            return ClassificationResult.of("CONFIGURATION", "PROPERTIES", techs, cats, sensitive, 0.75f);
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Code detection with false-positive guard
    // -------------------------------------------------------------------------

    /**
     * Code detection uses a two-gate approach:
     * <ol>
     *   <li>Language-specific STRONG markers (imports, class declarations, brackets)</li>
     *   <li>A false-positive guard: ordinary English prose must NOT trigger code detection
     *       just because it mentions a technology name.</li>
     * </ol>
     */
    private ClassificationResult checkCode(String text, boolean sensitive) {
        // TypeScript (before JS, more specific)
        if (isTypeScript(text)) {
            List<String> techs = new ArrayList<>();
            techs.add("TYPESCRIPT");
            boolean isReact = hasReactMarkers(text);
            if (isReact) techs.add("REACT");
            if (text.contains("NestJS") || text.contains("@Module") || text.contains("@Injectable")) techs.add("NODE_JS");
            List<String> cats = isReact ? List.of("WEB", "PROGRAMMING") : List.of("PROGRAMMING");
            return ClassificationResult.of("CODE", "TYPESCRIPT", techs, cats, sensitive, 0.93f);
        }
        // Java / Spring Boot
        if (isJava(text)) {
            List<String> techs = new ArrayList<>();
            if (isSpringBoot(text)) techs.add("SPRING_BOOT");
            techs.add("JAVA");
            return ClassificationResult.of("CODE", "JAVA", techs, List.of("PROGRAMMING"), sensitive, 0.95f);
        }
        // Kotlin
        if (isKotlin(text)) {
            List<String> techs = new ArrayList<>();
            techs.add("KOTLIN");
            if (isSpringBoot(text)) techs.add("SPRING_BOOT");
            return ClassificationResult.of("CODE", "KOTLIN", techs, List.of("PROGRAMMING"), sensitive, 0.9f);
        }
        // Python
        if (isPython(text)) {
            List<String> techs = new ArrayList<>();
            techs.add("PYTHON");
            if (text.contains("django") || text.contains("Django")) techs.add("DJANGO");
            if (text.contains("flask") || text.contains("Flask")) techs.add("FLASK");
            if (text.contains("fastapi") || text.contains("FastAPI")) techs.add("FASTAPI");
            if (text.contains("pandas") || text.contains("pd.")) techs.add("PANDAS");
            if (text.contains("numpy") || text.contains("np.")) techs.add("NUMPY");
            return ClassificationResult.of("CODE", "PYTHON", techs, List.of("PROGRAMMING"), sensitive, 0.95f);
        }
        // JavaScript (after TS since TS is more specific)
        if (isJavaScript(text)) {
            List<String> techs = new ArrayList<>();
            techs.add("JAVASCRIPT");
            boolean isReact = hasReactMarkers(text);
            if (isReact) techs.add("REACT");
            if (text.contains("express") || text.contains("Express")) techs.add("NODE_JS");
            List<String> cats = isReact ? List.of("WEB", "PROGRAMMING") : List.of("PROGRAMMING");
            return ClassificationResult.of("CODE", "JAVASCRIPT", techs, cats, sensitive, 0.9f);
        }
        // Go
        if (isGo(text)) {
            return ClassificationResult.of("CODE", "GO", List.of("GO"), List.of("PROGRAMMING"), sensitive, 0.9f);
        }
        // Rust
        if (isRust(text)) {
            return ClassificationResult.of("CODE", "RUST", List.of("RUST"), List.of("PROGRAMMING"), sensitive, 0.9f);
        }
        // C# / .NET
        if (isCSharp(text)) {
            return ClassificationResult.of("CODE", "CSHARP", List.of("DOTNET"), List.of("PROGRAMMING"), sensitive, 0.9f);
        }
        // PHP
        if (isPhp(text)) {
            return ClassificationResult.of("CODE", "PHP", List.of("PHP"), List.of("PROGRAMMING"), sensitive, 0.9f);
        }
        // HTML/CSS (after all code checks)
        if (isHtml(text)) {
            List<String> techs = new ArrayList<>();
            if (hasReactMarkers(text)) techs.add("REACT");
            return ClassificationResult.of("CODE", "HTML", techs, List.of("WEB"), sensitive, 0.88f);
        }
        if (isCss(text)) {
            return ClassificationResult.of("CODE", "CSS", List.of(), List.of("WEB"), sensitive, 0.88f);
        }
        // Shell / Bash script (multi-line without a shebang that didn't match commands)
        if (isShellScript(text)) {
            return ClassificationResult.of("CODE", "SHELL", List.of(), List.of("DEVOPS"), sensitive, 0.82f);
        }
        return null;
    }

    // ---- Individual language detectors ----

    private boolean isSpringBoot(String t) {
        return t.contains("@SpringBootApplication") || t.contains("@RestController")
                || t.contains("@GetMapping") || t.contains("@PostMapping") || t.contains("@PutMapping")
                || t.contains("@DeleteMapping") || t.contains("@Service") || t.contains("@Repository")
                || t.contains("@Autowired") || t.contains("import org.springframework.");
    }

    private boolean hasReactMarkers(String t) {
        return t.contains("import React") || t.contains("useState(") || t.contains("useEffect(")
                || t.contains("useContext(") || t.contains("useMemo(") || t.contains("useCallback(")
                || t.contains("JSX.Element") || t.contains("React.FC");
    }

    private boolean isTypeScript(String t) {
        if (isPython(t)) {
            return false;
        }
        // Must have TS-specific syntax, not just keywords
        boolean hasTsType = t.contains(": string") || t.contains(": number") || t.contains(": boolean")
                || t.contains(": void") || t.contains(": any") || t.contains(": unknown") || t.contains("interface ")
                || (t.contains("type ") && t.contains(" = {")) || t.contains("<T>")
                || t.matches("(?s).*\\bas\\s+([A-Z]\\w*|string|number|boolean|any|unknown|const)\\b.*");
        boolean hasJsDecl = t.contains("const ") || t.contains("let ") || t.contains("function ")
                || (t.contains("import ") && (t.contains("from '") || t.contains("from \"")))
                || t.contains("export ");
        return hasTsType && hasJsDecl && hasSyntaxBrackets(t);
    }

    private boolean isJava(String t) {
        boolean standardJava = t.contains("public class ") || t.contains("private class ")
                || t.contains("protected class ") || t.contains("abstract class ")
                || t.contains("public interface ") || t.contains("public enum ")
                || t.contains("public static void main") || t.contains("System.out.println(")
                || t.contains("System.out.print(") || t.contains("System.err.print")
                || t.contains("import java.") || t.contains("import jakarta.")
                || t.contains("import javax.") || t.contains("@Override")
                || t.contains("@Entity") || t.contains("@Table")
                || (t.contains("package ") && t.contains(";") && t.lines().anyMatch(l -> l.trim().startsWith("package ")));

        boolean javaGenericsOrCollections = (t.contains("HashMap<") || t.contains("ArrayList<")
                || t.contains("LinkedList<") || t.contains("HashSet<") || t.contains("TreeMap<")
                || t.contains("TreeSet<") || t.contains("ConcurrentHashMap<")
                || t.contains("Map<") || t.contains("List<") || t.contains("Set<") || t.contains("Optional<"))
                && (t.contains("new HashMap") || t.contains("new ArrayList") || t.contains("new LinkedList")
                    || t.contains("new HashSet") || t.contains("new TreeMap") || t.contains("new TreeSet")
                    || t.contains(".put(") || t.contains(".get(") || t.contains(".add(")
                    || t.contains(".containsKey(") || t.contains(".stream()")
                    || t.contains("List.of(") || t.contains("Map.of(") || t.contains("Set.of("))
                && t.contains(";");

        boolean javaInstantiations = (t.contains("new HashMap") || t.contains("new ArrayList")
                || t.contains("new LinkedList") || t.contains("new HashSet")
                || t.contains("new StringBuilder(") || t.contains("new StringBuffer(")
                || t.contains("new ConcurrentHashMap") || t.contains("new TreeMap") || t.contains("new TreeSet"))
                && t.contains(";") && (t.contains("<") || t.contains("("));

        return (standardJava || javaGenericsOrCollections || javaInstantiations)
                && hasSyntaxBrackets(t);
    }

    private boolean isKotlin(String t) {
        return (t.contains("fun ") && t.contains(":") && (t.contains("{") || t.contains("=>")))
                && (t.contains("val ") || t.contains("var ") || t.contains("data class ")
                || t.contains("object ") || t.contains("companion object"))
                && !t.contains("public class "); // not Java
    }

    private boolean isPython(String t) {
        // Guard: must not look like Java or C-family languages (semicolons with braces or standard Java keywords)
        boolean looksLikeJavaOrC = (t.contains("{") && t.contains("}") && t.contains(";"))
                || t.contains("public class ") || t.contains("private ") || t.contains("System.out.")
                || t.contains("import java.") || t.contains("import jakarta.") || t.contains("namespace ");
        if (looksLikeJavaOrC) return false;

        // Python imports: lines starting with 'import ' or 'from ... import '
        // Check line-by-line so that dictionaries/sets in later lines don't disqualify the import
        boolean hasPythonImport = t.lines().anyMatch(line -> {
            String l = line.trim();
            return (l.startsWith("import ") || l.startsWith("from "))
                    && !l.endsWith(";")
                    && !l.contains(";")
                    && !l.contains("{") // import statement itself shouldn't be JS destructuring: import { x } from 'y'
                    && !l.contains(" from '") // not JS/TS: import x from 'y'
                    && !l.contains(" from \"");
        });

        boolean hasDefColon = t.contains("def ") && t.contains("(") && t.contains(":") && t.contains("\n");
        boolean hasPyClass = t.contains("class ") && t.contains(":") && !t.contains("{") && !t.contains(";");
        boolean hasPrint = t.contains("print(") && !t.contains(";") && !t.contains("System.out");
        boolean hasMainGuard = t.contains("if __name__ ==") || t.contains("if __name__==");
        boolean hasPandasOrNumpy = (t.contains("pd.") || t.contains("np.") || t.contains("plt."))
                && (t.contains("read_csv") || t.contains("DataFrame") || t.contains("Series")
                    || t.contains("array(") || t.contains("zeros(") || t.contains("plot("));

        return hasDefColon || hasPythonImport || (hasPyClass && !t.contains("{")) || hasPrint || hasMainGuard || hasPandasOrNumpy;
    }

    private boolean isJavaScript(String t) {
        // Must have both JS keywords AND syntax brackets/semicolons — prose won't have this
        boolean hasKeyword = t.contains("const ") || t.contains("let ") || t.contains("var ")
                || t.contains("function ") || t.contains("console.log(") || t.contains("=> {")
                || t.contains("async ") || t.contains("await ") || t.contains("require(")
                || t.contains("module.exports");
        return hasKeyword && hasSyntaxBrackets(t);
    }

    private boolean isGo(String t) {
        return t.contains("package ") && t.contains("import (") && t.contains("func ")
                && t.contains("{") && t.contains("}");
    }

    private boolean isRust(String t) {
        return (t.contains("fn ") && t.contains("->") && t.contains("{"))
                && (t.contains("let mut ") || t.contains("impl ") || t.contains("use std::")
                || t.contains("pub fn") || t.contains("struct ") || t.contains("enum "));
    }

    private boolean isCSharp(String t) {
        return (t.contains("using System") || t.contains("namespace ") || t.contains("public class ")
                || t.contains("static void Main") || t.contains("Console.WriteLine"))
                && t.contains("{") && t.contains("}") && t.contains(";");
    }

    private boolean isPhp(String t) {
        return t.contains("<?php") || (t.contains("$") && t.contains("->") && t.contains(";")
                && (t.contains("function ") || t.contains("class ")));
    }

    private boolean isHtml(String t) {
        return (t.contains("<!DOCTYPE html") || t.contains("<html") || t.contains("</html>")
                || t.contains("<head>") || t.contains("<body>") || t.contains("<div") || t.contains("</div>"))
                && t.contains(">") && t.contains("</");
    }

    private boolean isCss(String t) {
        return t.matches("(?s).*[.#]?[a-zA-Z][a-zA-Z0-9_-]*\\s*\\{[^}]+\\}.*")
                && (t.contains("px") || t.contains("em") || t.contains("color") || t.contains("margin")
                || t.contains("padding") || t.contains("font") || t.contains("display:"));
    }

    private boolean isShellScript(String t) {
        // Multi-line with shell constructs
        long lines = t.lines().count();
        if (lines < 2) return false;
        return t.contains("if [") || t.contains("fi") || t.contains("done") || t.contains("for ")
                || (t.contains("then") && t.contains("fi"))
                || (t.contains("$") && t.contains("=") && t.contains("\n"));
    }

    private boolean isMarkdown(String t) {
        return MARKDOWN_PATTERN.matcher(t).find() && t.lines().count() >= 2;
    }

    /** Checks that text has at least one { } or ( ) pair — guards against pure prose. */
    private boolean hasSyntaxBrackets(String t) {
        return (t.contains("{") && t.contains("}")) || (t.contains("(") && t.contains(")") && t.contains(";"));
    }
}
