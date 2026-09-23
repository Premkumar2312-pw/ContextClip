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
 *   <li>Image Data URL</li>
 *   <li>Phone number (with IPv4 guard)</li>
 *   <li>Email</li>
 *   <li>URL</li>
 *   <li>Identifiers / Network (IPv4, IPv6, MAC, UUID, Hashes)</li>
 *   <li>Error / Stack trace (compiler errors, runtime exceptions, build errors)</li>
 *   <li>Mathematical / Scientific formulas (LaTeX, Unicode math, calculus, physics)</li>
 *   <li>SQL</li>
 *   <li>Terminal command (Linux, Windows, Git, Docker, K8s, Package Managers)</li>
 *   <li>Code (C, C++, C#, Go, Rust, Java, Python, TypeScript, JavaScript, Swift, Kotlin, etc.)</li>
 *   <li>Configuration (Dockerfile, Makefile, YAML, TOML, .properties, POM)</li>
 *   <li>Structured Data (JSON, XML, CSV, File path)</li>
 *   <li>Markdown</li>
 *   <li>Multilingual natural text (Tamil, Hindi, Japanese, Chinese, Arabic, Cyrillic, etc.)</li>
 *   <li>Plain text fallback</li>
 * </ol>
 *
 * <p>Normal natural language sentences that merely mention technology names (e.g. "I am learning
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
    // URL / Email / UUID / IP / MAC / Hashes / File path
    // -------------------------------------------------------------------------

    private static final Pattern URL_PATTERN = Pattern.compile(
            "^https?://\\S+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)(:\\d{1,5})?$");
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|::([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}|fe80::[0-9a-fA-F:]+)$");
    private static final Pattern MAC_PATTERN = Pattern.compile(
            "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
    private static final Pattern HASH_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{32}|[0-9a-fA-F]{40}|[0-9a-fA-F]{64})$");
    private static final Pattern FILE_PATH_WINDOWS = Pattern.compile(
            "^[A-Za-z]:\\\\(\\S+\\\\)*\\S*$");
    private static final Pattern FILE_PATH_UNIX = Pattern.compile(
            "^(/[^/\\s]+)+/?$");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?(\\d{1,4}[-.\\s]?)?(\\(?\\d{2,4}\\)?[-.\\s]?)?[\\d.\\s-]{6,14}\\d$");

    // -------------------------------------------------------------------------
    // Mathematical & Scientific Formulas (LaTeX + Unicode Math)
    // -------------------------------------------------------------------------

    private static final Pattern LATEX_MATH_COMMAND = Pattern.compile(
            "\\\\(int|iint|iiint|oint|sum|prod|coprod|frac|dfrac|tfrac|partial|nabla|sqrt|lim|limsup|liminf|infty|" +
            "alpha|beta|gamma|delta|epsilon|varepsilon|zeta|eta|theta|vartheta|iota|kappa|lambda|mu|nu|xi|pi|varpi|" +
            "rho|varrho|sigma|varsigma|tau|upsilon|phi|varphi|chi|psi|omega|" +
            "Gamma|Delta|Theta|Lambda|Xi|Pi|Sigma|Upsilon|Phi|Psi|Omega|" +
            "times|div|cdot|pm|mp|approx|neq|leq|geq|ll|gg|in|notin|subset|supset|subseteq|supseteq|" +
            "forall|exists|nexists|emptyset|to|rightarrow|leftarrow|Rightarrow|Leftarrow|Leftrightarrow|" +
            "mathbf|mathrm|mathit|mathbb|mathcal|begin\\{(matrix|pmatrix|bmatrix|vmatrix|Vmatrix|equation|align|gather)\\})"
    );

    private static final Pattern UNICODE_MATH_SYMBOLS = Pattern.compile(
            "[∫∬∭∮∯∰∇∂∑∏√∛∜∞≈≠≤≥∈∉⊂⊃⊆⊇∀∃∄∅±×÷·←→↔⇐⇒⇔↦∝∼≅≡≪≫⊕⊖⊗⊘⊙⊢⊨∧∨∩∪∴∵∶∷]"
    );

    private static final Pattern GREEK_MATH_LETTERS = Pattern.compile(
            "[αβγδεζηθικλμνξπρστυφχψωΓΔΘΛΞΠΣΦΨΩ]"
    );

    private static final Pattern MATH_SUPERSCRIPTS_SUBSCRIPTS = Pattern.compile(
            "[⁰¹²³⁴⁵⁶⁷⁸⁹⁺⁻⁼⁽⁾ⁿⁱ₀₁₂₃₄₅₆₇₈₉₊₋₌₍₎]"
    );

    // -------------------------------------------------------------------------
    // Multilingual Scripts
    // -------------------------------------------------------------------------

    private static final Pattern SCRIPT_TAMIL     = Pattern.compile("[\\u0B80-\\u0BFF]");
    private static final Pattern SCRIPT_HINDI     = Pattern.compile("[\\u0900-\\u097F]");
    private static final Pattern SCRIPT_TELUGU    = Pattern.compile("[\\u0C00-\\u0C7F]");
    private static final Pattern SCRIPT_KANNADA   = Pattern.compile("[\\u0C80-\\u0CFF]");
    private static final Pattern SCRIPT_MALAYALAM = Pattern.compile("[\\u0D00-\\u0D7F]");
    private static final Pattern SCRIPT_BENGALI   = Pattern.compile("[\\u0980-\\u09FF]");
    private static final Pattern SCRIPT_GUJARATI  = Pattern.compile("[\\u0A80-\\u0AFF]");
    private static final Pattern SCRIPT_PUNJABI   = Pattern.compile("[\\u0A00-\\u0A7F]");
    private static final Pattern SCRIPT_ARABIC    = Pattern.compile("[\\u0600-\\u06FF\\u0750-\\u077F\\uFB50-\\uFDFF\\uFE70-\\uFEFF]");
    private static final Pattern SCRIPT_JAPANESE  = Pattern.compile("[\\u3040-\\u309F\\u30A0-\\u30FF]");
    private static final Pattern SCRIPT_CHINESE   = Pattern.compile("[\\u4E00-\\u9FFF]");
    private static final Pattern SCRIPT_KOREAN    = Pattern.compile("[\\uAC00-\\uD7AF\\u1100-\\u11FF]");
    private static final Pattern SCRIPT_CYRILLIC  = Pattern.compile("[\\u0400-\\u04FF]");

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
    private static final Pattern PYTHON_EXCEPTION_LINE = Pattern.compile(
            "(?m)^([A-Za-z0-9_]*(?:Exception|Error)):\\s+.*");
    private static final Pattern JAVA_EXCEPTION_LINE = Pattern.compile(
            "(?m)^(Exception in thread \"[^\"]+\"|Caused by:)\\s+([a-zA-Z0-9_.$]+(?:Exception|Error)):?\\s*.*");
    private static final Pattern BUILD_OR_TOOL_ERROR = Pattern.compile(
            "(?m)^(npm (ERR!|error)|\\[ERROR\\] Failed to execute goal|BUILD FAILURE|FAILURE: Build failed with an exception|error\\[E\\d+\\]:|error TS\\d+:|fatal: not a git repository|fatal: destination path|error: failed to push some refs|Error response from daemon:|HTTP\\s+(4\\d\\d|5\\d\\d)|status code (4\\d\\d|5\\d\\d)|ECONNREFUSED|ETIMEDOUT)");

    // -------------------------------------------------------------------------
    // Terminal commands
    // -------------------------------------------------------------------------

    private static final Pattern DOCKER_CMD = Pattern.compile(
            "^(docker\\s+compose|docker-compose|docker|podman)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern GIT_CMD = Pattern.compile(
            "^git\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern MAVEN_CMD = Pattern.compile(
            "^(\\./)?(mvn|mvnw)\\s+.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern GRADLE_CMD = Pattern.compile(
            "^(\\./)?(gradle|gradlew)\\s+.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern NODE_CMD = Pattern.compile(
            "^(node|nodejs|npm|npx|yarn|pnpm|bun|deno)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PYTHON_CMD = Pattern.compile(
            "^(python[23]?|py|pip[23]?|pytest|poetry|uv|pipenv|conda)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVA_CMD = Pattern.compile(
            "^(java|javac|javadoc|jar)\\s+.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUST_CMD = Pattern.compile(
            "^cargo\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern GO_CMD = Pattern.compile(
            "^go\\s+(run|build|test|get|mod|install|fmt|vet)\\s+.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOTNET_CMD = Pattern.compile(
            "^dotnet\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPOSER_CMD = Pattern.compile(
            "^composer\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUBY_CMD = Pattern.compile(
            "^(gem|bundle|rake|ruby)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern KUBECTL_CMD = Pattern.compile(
            "^(kubectl|helm)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern AWS_CMD = Pattern.compile(
            "^aws\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PKG_MGR_CMD = Pattern.compile(
            "^(brew|apt|apt-get|yum|dnf|pacman|winget|choco)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHELL_CMD = Pattern.compile(
            "^(cd|ls|dir|cat|echo|curl|wget|sudo|chmod|chown|mkdir|rmdir|rm|ps|kill|top|htop|df|du|export|set|setx|grep|find|sed|awk|tail|head|ssh|scp|cp|mv|touch|ping|traceroute|netstat|ifconfig|ip|systemctl|journalctl|service|cls|clear|tasklist|taskkill|ipconfig)\\s.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SHELL_SHEBANG = Pattern.compile(
            "^#!\\s*/.*/(bash|sh|zsh|fish|python|ruby|perl|node)\\b");
    private static final Pattern POWERSHELL_CMD = Pattern.compile(
            "^(Get-|Set-|New-|Remove-|Invoke-|Start-|Stop-|Write-|Read-|Import-|Export-|Add-|Test-|Update-|Install-|Uninstall-|powershell|pwsh)\\b.*",
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
    private static final Pattern TOML_STRUCTURE = Pattern.compile(
            "(^|\\n)\\s*\\[[a-zA-Z0-9._-]+\\]\\s*\\n");
    private static final Pattern MAKEFILE_STRUCTURE = Pattern.compile(
            "(^|\\n)(\\.PHONY:|[a-zA-Z0-9._-]+:.*\\n\\t\\S+)");

    // -------------------------------------------------------------------------
    // CSV & Markdown
    // -------------------------------------------------------------------------

    private static final Pattern CSV_PATTERN = Pattern.compile(
            "^([^,\\n]+,){2,}[^,\\n]*(\\n([^,\\n]+,){2,}[^,\\n]*){1,}$");
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

        // 5. Network & Identifiers (UUID, IPv4, IPv6, MAC, Hash)
        if (UUID_PATTERN.matcher(trimmed).matches()) {
            return ClassificationResult.of("UUID", null, List.of(), List.of("IDENTIFIER"), isSensitive, 0.98f);
        }
        if (IPV4_PATTERN.matcher(trimmed).matches()) {
            return ClassificationResult.of("IP_ADDRESS", null, List.of(), List.of("NETWORKING"), isSensitive, 1.0f);
        }
        if (IPV6_PATTERN.matcher(trimmed).matches()) {
            return ClassificationResult.of("IP_ADDRESS", null, List.of(), List.of("NETWORKING"), isSensitive, 0.98f);
        }
        if (MAC_PATTERN.matcher(trimmed).matches()) {
            return ClassificationResult.of("MAC_ADDRESS", null, List.of(), List.of("NETWORKING"), isSensitive, 0.98f);
        }
        if (HASH_PATTERN.matcher(trimmed).matches()) {
            return ClassificationResult.of("HASH", null, List.of(), List.of("IDENTIFIER"), isSensitive, 0.95f);
        }

        // 6. Stack trace / error message
        ClassificationResult errResult = checkError(trimmed, isSensitive);
        if (errResult != null) return errResult;

        // 7. Mathematical / Scientific formulas (LaTeX & Unicode Math)
        ClassificationResult mathResult = checkMath(trimmed, isSensitive);
        if (mathResult != null) return mathResult;

        // 8. SQL
        if (isSql(trimmed)) {
            return ClassificationResult.of("SQL", "SQL", List.of("SQL"), List.of("DATABASE"), isSensitive, 0.98f);
        }

        // 9. Terminal commands (first non-empty line must match — guards against prose mentioning a tool)
        ClassificationResult cmdResult = checkCommand(trimmed, isSensitive);
        if (cmdResult != null) return cmdResult;

        // 10. Code (language-specific, with false-positive guard)
        ClassificationResult codeResult = checkCode(trimmed, isSensitive);
        if (codeResult != null) return codeResult;

        // 11. Configuration files
        ClassificationResult configResult = checkConfiguration(trimmed, isSensitive);
        if (configResult != null) return configResult;

        // 12. Structured Data (JSON / XML / CSV / File Path)
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

        if (!trimmed.contains("\n") && (FILE_PATH_WINDOWS.matcher(trimmed).matches() || FILE_PATH_UNIX.matcher(trimmed).matches())) {
            return ClassificationResult.of("FILE_PATH", null, List.of(), List.of("FILESYSTEM"), isSensitive, 0.9f);
        }

        if (CSV_PATTERN.matcher(trimmed).matches()) {
            return ClassificationResult.of("CSV", null, List.of(), List.of("DATA"), isSensitive, 0.85f);
        }

        // 13. Markdown
        if (isMarkdown(trimmed)) {
            return ClassificationResult.of("MARKDOWN", null, List.of(), List.of("DOCUMENTATION"), isSensitive, 0.8f);
        }

        // 14. Multilingual natural text
        ClassificationResult multiResult = checkMultilingual(trimmed, isSensitive);
        if (multiResult != null) return multiResult;

        // 15. Sensitive fallback (if sensitive but no other type matched)
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
        boolean isJavaTrace       = JAVA_STACKTRACE.matcher(text).find();
        boolean isPyTrace         = PYTHON_TRACEBACK.matcher(text).find();
        boolean isExcPattern      = EXCEPTION_PATTERN.matcher(text).find();
        boolean isPyException     = PYTHON_EXCEPTION_LINE.matcher(text).find();
        boolean isJavaException   = JAVA_EXCEPTION_LINE.matcher(text).find();
        boolean isBuildOrToolErr  = BUILD_OR_TOOL_ERROR.matcher(text).find();
        boolean hasErrorLabel     = text.contains("FATAL:") || text.contains("ERROR:") || text.contains("FAILED:")
                || text.startsWith("fatal: ") || text.startsWith("error: ") || text.contains("cannot find symbol")
                || text.contains("incompatible types");

        if (!isJavaTrace && !isPyTrace && !isExcPattern && !isPyException && !isJavaException && !isBuildOrToolErr && !hasErrorLabel) {
            return null;
        }

        List<String> techs = new ArrayList<>();
        String language = null;
        String lower = text.toLowerCase();

        if (isPyTrace || isPyException || lower.contains("traceback") || text.contains(".py\"") || lower.contains("modulenotfounderror")) {
            techs.add("PYTHON"); language = "PYTHON";
        } else if (isJavaTrace || isJavaException || lower.contains("springframework") || text.contains(".java:") || text.contains("javac:")) {
            if (lower.contains("springframework") || lower.contains("spring")) techs.add("SPRING_BOOT");
            techs.add("JAVA"); language = "JAVA";
        } else if (lower.contains("npm err") || lower.contains("npm error") || lower.contains("eresolve")) {
            techs.add("NODE_JS"); language = "JAVASCRIPT";
        } else if (lower.contains("error ts") || lower.contains("type '") && lower.contains("' is not assignable to type")) {
            techs.add("TYPESCRIPT"); language = "TYPESCRIPT";
        } else if (lower.contains("error[e") || lower.contains("rustc")) {
            techs.add("RUST"); language = "RUST";
        } else if (lower.contains("react") || lower.contains(".tsx:") || lower.contains(".jsx:")) {
            techs.add("REACT"); language = "TYPESCRIPT";
        } else if (lower.contains("node") || lower.contains(".js:")) {
            techs.add("NODE_JS"); language = "JAVASCRIPT";
        } else if (lower.contains("fatal: not a git repository") || lower.contains("failed to push some refs") || lower.contains("git")) {
            techs.add("GIT");
        } else if (lower.contains("docker") || lower.contains("error response from daemon")) {
            techs.add("DOCKER");
        } else if (lower.contains("failed to execute goal") || lower.contains("build failure")) {
            techs.add("MAVEN");
        } else if (lower.contains("build failed with an exception")) {
            techs.add("GRADLE");
        }

        if (text.contains("PSQLException") || text.contains("SQLException")) techs.add("POSTGRESQL");

        boolean isTrace = isJavaTrace || isPyTrace || isExcPattern || text.contains("\tat ");
        String type = isTrace ? "STACK_TRACE" : "ERROR_MESSAGE";
        List<String> cats = List.of("DEBUG", "TROUBLESHOOTING");
        return ClassificationResult.of(type, language, techs, cats, sensitive, 0.95f);
    }

    // -------------------------------------------------------------------------
    // Mathematical & Scientific Formulas (LaTeX + Unicode Math)
    // -------------------------------------------------------------------------

    private ClassificationResult checkMath(String text, boolean sensitive) {
        boolean hasLatex = LATEX_MATH_COMMAND.matcher(text).find();
        boolean hasUnicodeMath = UNICODE_MATH_SYMBOLS.matcher(text).find();
        boolean hasGreek = GREEK_MATH_LETTERS.matcher(text).find();
        boolean hasScripts = MATH_SUPERSCRIPTS_SUBSCRIPTS.matcher(text).find();

        if (!hasLatex && !hasUnicodeMath && !(hasGreek && (hasScripts || text.contains("=") || text.contains("^")))) {
            return null;
        }

        // False-positive guard against ordinary prose
        if (!hasLatex) {
            long symbolCount = text.codePoints().filter(c -> {
                String s = Character.toString(c);
                return UNICODE_MATH_SYMBOLS.matcher(s).matches()
                        || GREEK_MATH_LETTERS.matcher(s).matches()
                        || MATH_SUPERSCRIPTS_SUBSCRIPTS.matcher(s).matches();
            }).count();

            if (symbolCount < 2 && !text.contains("=") && !text.contains("^") && !text.contains("/") && !text.contains("dx")) {
                return null;
            }
        }

        List<String> techs = new ArrayList<>();
        List<String> cats = new ArrayList<>();
        cats.add("MATHEMATICS");

        String lower = text.toLowerCase();
        boolean isCalculus = text.contains("\\int") || text.contains("∫") || text.contains("\\partial") || text.contains("∂")
                || text.contains("dx") || text.contains("dy") || text.contains("dt") || text.contains("\\lim") || text.contains("\\sum") || text.contains("∑");

        // Physics domain detection (Navier-Stokes, Schrödinger, Maxwell)
        boolean isNavierStokes = (text.contains("\\nabla") || text.contains("∇") || text.contains("nabla"))
                && (text.contains("\\partial") || text.contains("∂"))
                && (text.contains("\\rho") || text.contains("ρ") || text.contains("\\nu") || text.contains("ν") || lower.contains("navier") || text.contains("∇²") || text.contains("\\nabla^2"));

        boolean isSchrodinger = (text.contains("Ψ") || text.contains("\\psi") || text.contains("\\Psi") || lower.contains("schrodinger") || lower.contains("schrödinger"))
                && (text.contains("ℏ") || text.contains("\\hbar") || text.contains("Ĥ") || text.contains("\\hat{H}") || text.contains("iℏ"));

        boolean isMaxwell = (text.contains("∇ ·") || text.contains("\\nabla \\cdot") || text.contains("∇ ×") || text.contains("\\nabla \\times") || lower.contains("maxwell"))
                && (text.contains("E") || text.contains("B") || text.contains("D") || text.contains("H"));

        // Statistics / distributions (Gamma, Beta, Normal, Poisson, Binomial)
        boolean isGamma = ((text.contains("Γ") || text.contains("\\Gamma") || lower.contains("gamma"))
                && (text.contains("k-1") || text.contains("k - 1") || text.contains("e^{-") || text.contains("e^(-") || text.contains("theta") || text.contains("θ")))
                || ((text.contains("k-1") || text.contains("k - 1")) && (text.contains("e^{-") || text.contains("e^(-")));

        boolean isStats = isGamma || text.contains("Normal(") || text.contains("Poisson(") || text.contains("Binomial(")
                || (text.contains("P(") && text.contains("|") && text.contains(")"))
                || text.contains("\\sigma") || text.contains("σ") || text.contains("\\mu") || text.contains("μ");

        if (isNavierStokes || isSchrodinger || isMaxwell) {
            techs.add("PHYSICS");
            cats.add("SCIENCE");
        }
        if (isStats) {
            techs.add("STATISTICS");
        }
        if (isCalculus) {
            techs.add("CALCULUS");
        }

        if (hasLatex) {
            techs.add(0, "LATEX");
            return ClassificationResult.of("FORMULA", "LATEX", techs, cats, sensitive, 0.96f);
        } else {
            if (techs.isEmpty()) techs.add("MATHEMATICS");
            return ClassificationResult.of("FORMULA", "UNICODE_MATH", techs, cats, sensitive, 0.95f);
        }
    }

    // -------------------------------------------------------------------------
    // Multilingual Script Detection
    // -------------------------------------------------------------------------

    private ClassificationResult checkMultilingual(String text, boolean sensitive) {
        String lang = null;
        if (hasScriptMatches(text, SCRIPT_TAMIL, 2)) {
            lang = "TAMIL";
        } else if (hasScriptMatches(text, SCRIPT_HINDI, 2)) {
            lang = "HINDI";
        } else if (hasScriptMatches(text, SCRIPT_TELUGU, 2)) {
            lang = "TELUGU";
        } else if (hasScriptMatches(text, SCRIPT_KANNADA, 2)) {
            lang = "KANNADA";
        } else if (hasScriptMatches(text, SCRIPT_MALAYALAM, 2)) {
            lang = "MALAYALAM";
        } else if (hasScriptMatches(text, SCRIPT_BENGALI, 2)) {
            lang = "BENGALI";
        } else if (hasScriptMatches(text, SCRIPT_GUJARATI, 2)) {
            lang = "GUJARATI";
        } else if (hasScriptMatches(text, SCRIPT_PUNJABI, 2)) {
            lang = "PUNJABI";
        } else if (hasScriptMatches(text, SCRIPT_ARABIC, 2)) {
            lang = "ARABIC";
        } else if (hasScriptMatches(text, SCRIPT_JAPANESE, 2)) {
            lang = "JAPANESE";
        } else if (hasScriptMatches(text, SCRIPT_CHINESE, 2)) {
            lang = "CHINESE";
        } else if (hasScriptMatches(text, SCRIPT_KOREAN, 2)) {
            lang = "KOREAN";
        } else if (hasScriptMatches(text, SCRIPT_CYRILLIC, 3)) {
            lang = "RUSSIAN";
        }

        if (lang != null) {
            return ClassificationResult.of("PLAIN_TEXT", lang, List.of(lang), List.of("COMMUNICATION", "GENERAL"), sensitive, 0.95f);
        }

        // Latin European languages detection (French, German, Spanish)
        String lower = " " + text.toLowerCase() + " ";
        if (text.matches(".*[éèêàçùôî].*") || (lower.contains(" le ") && lower.contains(" la ") && lower.contains(" et "))
                || lower.contains(" c'est ") || lower.contains(" dans le ")) {
            return ClassificationResult.of("PLAIN_TEXT", "FRENCH", List.of("FRENCH"), List.of("COMMUNICATION", "GENERAL"), sensitive, 0.85f);
        }
        if (text.matches(".*[äöüß].*") || (lower.contains(" der ") && lower.contains(" die ") && lower.contains(" und "))
                || lower.contains(" nicht ") || lower.contains(" das ist ")) {
            return ClassificationResult.of("PLAIN_TEXT", "GERMAN", List.of("GERMAN"), List.of("COMMUNICATION", "GENERAL"), sensitive, 0.85f);
        }
        if (text.matches(".*[ñáíóú¿¡].*") || (lower.contains(" el ") && lower.contains(" la ") && lower.contains(" y "))
                || lower.contains(" por favor ") || lower.contains(" de la ")) {
            return ClassificationResult.of("PLAIN_TEXT", "SPANISH", List.of("SPANISH"), List.of("COMMUNICATION", "GENERAL"), sensitive, 0.85f);
        }

        return null;
    }

    private boolean hasScriptMatches(String text, Pattern pattern, int minMatches) {
        var matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
            if (count >= minMatches) return true;
        }
        return false;
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
        String firstLine = text.lines().filter(l -> !l.isBlank()).findFirst().orElse("").trim();

        if (SHELL_SHEBANG.matcher(text).find()) {
            String lang = detectScriptLanguage(text);
            return ClassificationResult.of("COMMAND", lang, List.of(lang), List.of("DEVOPS"), sensitive, 0.9f);
        }
        if (DOCKER_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("DOCKER"), List.of("DEVOPS"), sensitive, 0.97f);
        }
        if (GIT_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("GIT"), List.of("DEVOPS"), sensitive, 0.97f);
        }
        if (KUBECTL_CMD.matcher(firstLine).matches()) {
            List<String> techs = firstLine.toLowerCase().startsWith("helm")
                    ? List.of("KUBERNETES", "HELM")
                    : List.of("KUBERNETES");
            return ClassificationResult.of("COMMAND", "SHELL", techs, List.of("DEVOPS"), sensitive, 0.97f);
        }
        if (AWS_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("AWS"), List.of("DEVOPS"), sensitive, 0.97f);
        }
        if (MAVEN_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("MAVEN"), List.of("DEVOPS", "BUILD"), sensitive, 0.97f);
        }
        if (GRADLE_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("GRADLE"), List.of("DEVOPS", "BUILD"), sensitive, 0.97f);
        }
        if (NODE_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("NODE_JS"), List.of("DEVOPS", "BUILD"), sensitive, 0.97f);
        }
        if (PYTHON_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "PYTHON", List.of("PYTHON"), List.of("DEVOPS"), sensitive, 0.97f);
        }
        if (RUST_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("RUST", "CARGO"), List.of("BUILD", "DEVOPS"), sensitive, 0.97f);
        }
        if (GO_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("GO"), List.of("BUILD", "DEVOPS"), sensitive, 0.97f);
        }
        if (DOTNET_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("DOTNET"), List.of("BUILD", "DEVOPS"), sensitive, 0.97f);
        }
        if (COMPOSER_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("PHP", "COMPOSER"), List.of("BUILD", "DEVOPS"), sensitive, 0.97f);
        }
        if (RUBY_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("RUBY"), List.of("BUILD", "DEVOPS"), sensitive, 0.97f);
        }
        if (JAVA_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("JAVA"), List.of("BUILD"), sensitive, 0.97f);
        }
        if (PKG_MGR_CMD.matcher(firstLine).matches()) {
            return ClassificationResult.of("COMMAND", "SHELL", List.of("SHELL"), List.of("DEVOPS", "SYSTEM"), sensitive, 0.92f);
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
        if (TOML_STRUCTURE.matcher(text).find() && (text.contains("=") || text.contains("\""))) {
            techs.add("TOML"); cats.add("CONFIGURATION");
            return ClassificationResult.of("CONFIGURATION", "TOML", techs, cats, sensitive, 0.9f);
        }
        if (MAKEFILE_STRUCTURE.matcher(text).find() && text.contains("\t")) {
            techs.add("MAKEFILE"); cats.add("BUILD"); cats.add("CONFIGURATION");
            return ClassificationResult.of("CONFIGURATION", "MAKEFILE", techs, cats, sensitive, 0.9f);
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

    private ClassificationResult checkCode(String text, boolean sensitive) {
        // C++ (before C)
        if (isCpp(text)) {
            return ClassificationResult.of("CODE", "CPP", List.of("CPP"), List.of("PROGRAMMING"), sensitive, 0.93f);
        }
        // C
        if (isC(text)) {
            return ClassificationResult.of("CODE", "C", List.of("C"), List.of("PROGRAMMING"), sensitive, 0.92f);
        }
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
        // C# / .NET (before Java so using System doesn't trigger Java public class)
        if (isCSharp(text)) {
            return ClassificationResult.of("CODE", "CSHARP", List.of("DOTNET", "CSHARP"), List.of("PROGRAMMING"), sensitive, 0.93f);
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
        // Swift
        if (isSwift(text)) {
            return ClassificationResult.of("CODE", "SWIFT", List.of("SWIFT"), List.of("PROGRAMMING"), sensitive, 0.92f);
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
        // Go
        if (isGo(text)) {
            return ClassificationResult.of("CODE", "GO", List.of("GO"), List.of("PROGRAMMING"), sensitive, 0.9f);
        }
        // Rust
        if (isRust(text)) {
            return ClassificationResult.of("CODE", "RUST", List.of("RUST"), List.of("PROGRAMMING"), sensitive, 0.9f);
        }
        // Dart
        if (isDart(text)) {
            return ClassificationResult.of("CODE", "DART", List.of("DART"), List.of("PROGRAMMING"), sensitive, 0.9f);
        }
        // Ruby
        if (isRuby(text)) {
            return ClassificationResult.of("CODE", "RUBY", List.of("RUBY"), List.of("PROGRAMMING"), sensitive, 0.9f);
        }
        // PHP
        if (isPhp(text)) {
            return ClassificationResult.of("CODE", "PHP", List.of("PHP"), List.of("PROGRAMMING"), sensitive, 0.9f);
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
        // Scala
        if (isScala(text)) {
            return ClassificationResult.of("CODE", "SCALA", List.of("SCALA"), List.of("PROGRAMMING"), sensitive, 0.9f);
        }
        // R
        if (isR(text)) {
            return ClassificationResult.of("CODE", "R", List.of("R"), List.of("PROGRAMMING"), sensitive, 0.88f);
        }
        // MATLAB
        if (isMatlab(text)) {
            return ClassificationResult.of("CODE", "MATLAB", List.of("MATLAB"), List.of("PROGRAMMING"), sensitive, 0.88f);
        }
        // Perl
        if (isPerl(text)) {
            return ClassificationResult.of("CODE", "PERL", List.of("PERL"), List.of("PROGRAMMING"), sensitive, 0.88f);
        }
        // Lua
        if (isLua(text)) {
            return ClassificationResult.of("CODE", "LUA", List.of("LUA"), List.of("PROGRAMMING"), sensitive, 0.88f);
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
        if (isCSharp(t)) {
            return false;
        }
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
                && !t.contains("public class ");
    }

    private boolean isSwift(String t) {
        return (t.contains("import UIKit") || t.contains("import SwiftUI") || t.contains("import Foundation"))
                || ((t.contains("func ") || t.contains("struct ")) && (t.contains("@State") || t.contains("@Binding") || t.contains(": View") || t.contains("guard let ")))
                && hasSyntaxBrackets(t);
    }

    private boolean isPython(String t) {
        if (t.contains("package ") || t.contains("func ") || t.contains("fn ")) {
            return false;
        }
        boolean looksLikeJavaOrC = (t.contains("{") && t.contains("}") && t.contains(";"))
                || t.contains("public class ") || t.contains("private ") || t.contains("System.out.")
                || t.contains("import java.") || t.contains("import jakarta.") || t.contains("namespace ");
        if (looksLikeJavaOrC) return false;

        boolean hasPythonImport = t.lines().anyMatch(line -> {
            String l = line.trim();
            return (l.startsWith("import ") || l.startsWith("from "))
                    && !l.endsWith(";")
                    && !l.contains(";")
                    && !l.contains("{")
                    && !l.contains(" from '")
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
        if (isRust(t) || isDart(t)) {
            return false;
        }
        boolean hasKeyword = t.contains("const ") || t.contains("let ") || t.contains("var ")
                || t.contains("function ") || t.contains("console.log(") || t.contains("=> {")
                || t.contains("async ") || t.contains("await ") || t.contains("require(")
                || t.contains("module.exports");
        return hasKeyword && hasSyntaxBrackets(t);
    }

    private boolean isCpp(String t) {
        boolean cppHeaders = t.contains("#include <iostream>") || t.contains("#include <vector>")
                || t.contains("#include <string>") || t.contains("#include <map>") || t.contains("#include <memory>");
        boolean cppSyntax = t.contains("std::cout") || t.contains("std::vector") || t.contains("std::string")
                || t.contains("std::endl") || t.contains("std::cin") || t.contains("nullptr")
                || t.contains("template <") || t.contains("template<") || (t.contains("namespace ") && t.contains("std"));
        return (cppHeaders || cppSyntax) && hasSyntaxBrackets(t);
    }

    private boolean isC(String t) {
        boolean cHeaders = t.contains("#include <stdio.h>") || t.contains("#include <stdlib.h>")
                || t.contains("#include <string.h>") || t.contains("#include <unistd.h>");
        boolean cKeywords = (t.contains("printf(") || t.contains("malloc(") || t.contains("free(") || t.contains("sizeof("))
                && (t.contains("int main(") || t.contains("void main("))
                && t.contains(";");
        return (cHeaders || cKeywords) && hasSyntaxBrackets(t) && !isCpp(t);
    }

    private boolean isGo(String t) {
        return (t.contains("package ") && (t.contains("func ") || t.contains("import (")))
                && (t.contains("fmt.Println") || t.contains(":=") || (t.contains("{") && t.contains("}")));
    }

    private boolean isRust(String t) {
        return (t.contains("fn ") && t.contains("->") && t.contains("{"))
                || ((t.contains("fn main()") || t.contains("pub fn ")) && (t.contains("let mut ") || t.contains("println!") || t.contains("use std::")));
    }

    private boolean isCSharp(String t) {
        boolean hasCsKeyword = t.contains("using System") || t.contains("Console.WriteLine")
                || t.contains("static void Main") || (t.contains("namespace ") && !t.contains("package ") && !t.contains("import java."));
        return hasCsKeyword && t.contains("{") && t.contains("}") && t.contains(";")
                && !t.contains("import java.") && !t.contains("System.out.");
    }

    private boolean isPhp(String t) {
        return t.contains("<?php") || (t.contains("$") && t.contains("->") && t.contains(";")
                && (t.contains("function ") || t.contains("class ")));
    }

    private boolean isRuby(String t) {
        boolean hasRubyMarkers = t.contains("attr_accessor") || t.contains("puts ") || t.contains("require '") || t.contains("#{");
        return (t.contains("def ") && t.contains("end") && hasRubyMarkers)
                && !t.contains("public class ") && !t.contains(";") && !t.contains("function ");
    }

    private boolean isDart(String t) {
        return (t.contains("import 'package:flutter/") || t.contains("Widget build(BuildContext context)"))
                || (t.contains("void main()") && t.contains("runApp("));
    }

    private boolean isScala(String t) {
        return (t.contains("object ") || t.contains("case class ") || t.contains("sealed trait "))
                && (t.contains("def main(args: Array[String])") || (t.contains("val ") && t.contains(": String")) || t.contains("extends App"))
                && hasSyntaxBrackets(t);
    }

    private boolean isR(String t) {
        return (t.contains("<- function(") || t.contains("data.frame(") || t.contains("ggplot("))
                && (t.contains("library(") || t.contains("<- c("));
    }

    private boolean isMatlab(String t) {
        return (t.contains("function [") || t.contains("clear all;") || t.contains("clc;"))
                && (t.contains("plot(") || t.contains("end\n") || t.endsWith("end"));
    }

    private boolean isPerl(String t) {
        return (t.contains("use strict;") || t.contains("use warnings;"))
                && (t.contains("my $") || t.contains("print \""));
    }

    private boolean isLua(String t) {
        return (t.contains("local function ") || (t.contains("local ") && t.contains("=")))
                && (t.contains("then\n") || t.contains("do\n"))
                && t.contains("end") && !t.contains("{");
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
