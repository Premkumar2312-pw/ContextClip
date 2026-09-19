package com.contextclip.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Deterministic, rule-based local content classifier for the ContextClip Desktop Agent.
 *
 * <p>Runs entirely in the JVM of the desktop agent — no network calls, no AI service.
 * Classification results are attached to the HTTP POST body sent to the backend, so the
 * backend can skip re-classifying trusted agent submissions.
 *
 * <p>Privacy guarantee: when {@code sensitive} is {@code true}, the caller MUST NOT log
 * the raw clipboard content.
 */
public class ContentClassifier {

    // -------------------------------------------------------------------------
    // Result value object
    // -------------------------------------------------------------------------

    public static final class Result {
        public final String type;
        public final String technology;   // primary (backward compat)
        public final String category;     // primary (backward compat)
        public final String language;     // may be null
        public final String technologies; // comma-joined, may be null
        public final String categories;   // comma-joined, may be null
        public final boolean sensitive;
        public final float confidence;

        private Result(String type, String technology, String category,
                       String language, List<String> technologies, List<String> categories,
                       boolean sensitive, float confidence) {
            this.type = type;
            this.technology = technology;
            this.category = category;
            this.language = language;
            this.technologies = (technologies == null || technologies.isEmpty()) ? null : String.join(",", technologies);
            this.categories = (categories == null || categories.isEmpty())
                    ? (category != null ? category : "GENERAL")
                    : String.join(",", categories);
            this.sensitive = sensitive;
            this.confidence = confidence;
        }

        public static Result of(String type, String lang, List<String> techs, List<String> cats,
                                 boolean sensitive, float confidence) {
            String primaryTech = (techs == null || techs.isEmpty()) ? "UNKNOWN" : techs.get(0);
            String primaryCat = (cats == null || cats.isEmpty()) ? "GENERAL" : cats.get(0);
            return new Result(type, primaryTech, primaryCat, lang, techs, cats, sensitive, confidence);
        }

        public static Result of(String type, String lang, List<String> techs, String cat,
                                 boolean sensitive, float confidence) {
            List<String> cats = (cat != null && !cat.equals("GENERAL")) ? List.of(cat) : List.of("GENERAL");
            return of(type, lang, techs, cats, sensitive, confidence);
        }

        public static Result plainText() {
            return new Result("PLAIN_TEXT", "UNKNOWN", "GENERAL", null, List.of(), List.of("GENERAL"), false, 1.0f);
        }

        public static Result sensitiveText() {
            return new Result("PLAIN_TEXT", "UNKNOWN", "SENSITIVE", null, List.of(), List.of("SENSITIVE"), true, 1.0f);
        }
    }

    // -------------------------------------------------------------------------
    // Sensitivity patterns
    // -------------------------------------------------------------------------

    private static final Pattern JWT_PAT = Pattern.compile(
            "eyJ[A-Za-z0-9_-]{4,}\\.eyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}");
    private static final Pattern API_KEY_PAT = Pattern.compile(
            "(?i)(api[_-]?key|apikey|secret[_-]?key|access[_-]?token|auth[_-]?token)\\s*[=:]\\s*[\\S]{8,}");
    private static final Pattern PRIVATE_KEY_PAT = Pattern.compile(
            "-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----");
    private static final Pattern PASSWORD_PAT = Pattern.compile(
            "(?i)(password|passwd|pwd)\\s*[=:]\\s*[\\S]{4,}");

    // -------------------------------------------------------------------------
    // URL / Email / UUID / IP
    // -------------------------------------------------------------------------

    private static final Pattern URL_PAT = Pattern.compile(
            "^https?://\\S+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PAT = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern UUID_PAT = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern IPV4_PAT = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)(:\\d{1,5})?$");
    private static final Pattern FILE_WIN_PAT = Pattern.compile("^[A-Za-z]:\\\\(\\S+\\\\)*\\S*$");
    private static final Pattern FILE_UNIX_PAT = Pattern.compile("^(/[^/\\s]+)+/?$");

    // -------------------------------------------------------------------------
    // SQL
    // -------------------------------------------------------------------------

    private static final Pattern SQL_START = Pattern.compile(
            "^\\s*(SELECT|INSERT\\s+INTO|UPDATE\\s+\\w+\\s+SET|DELETE\\s+FROM|CREATE\\s+(TABLE|DATABASE)|ALTER\\s+TABLE|DROP\\s+(TABLE|DATABASE)|TRUNCATE\\s+TABLE)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // -------------------------------------------------------------------------
    // Stack trace
    // -------------------------------------------------------------------------

    private static final Pattern JAVA_TRACE = Pattern.compile(
            "\\bat\\s+[a-zA-Z0-9_.$]+\\([a-zA-Z0-9_]+(\\.(java|kt))?:\\d+\\)");
    private static final Pattern PY_TRACE = Pattern.compile("Traceback \\(most recent call last\\):");

    // -------------------------------------------------------------------------
    // Terminal commands
    // -------------------------------------------------------------------------

    private static final Pattern DOCKER_CMD  = Pattern.compile("^(docker\\s+compose|docker-compose|docker)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern GIT_CMD     = Pattern.compile("^git\\s+\\S.*",   Pattern.CASE_INSENSITIVE);
    private static final Pattern MAVEN_CMD   = Pattern.compile("^(\\./)?(mvn|mvnw)\\s+.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern NPM_CMD     = Pattern.compile("^(npm|npx|yarn|pnpm)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PYTHON_CMD  = Pattern.compile("^(python3?|pip3?|pytest|poetry|uv)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVA_CMD    = Pattern.compile("^(java|javac|gradle|\\./gradlew|gradlew)\\s+.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern KUBECTL_CMD = Pattern.compile("^kubectl\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern AWS_CMD     = Pattern.compile("^aws\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHELL_CMD   = Pattern.compile(
            "^(cd|ls|dir|cat|echo|curl|wget|sudo|chmod|mkdir|rm|ps|kill|export|set|grep|find|sed|awk|tail|head|ssh|scp|cp|mv|touch|ping|systemctl|apt|yum|brew|choco)\\s.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PS_CMD      = Pattern.compile(
            "^(Get-|Set-|New-|Remove-|Invoke-|Start-|Stop-|Write-|Read-|Import-|Export-)\\w+.*",
            Pattern.CASE_INSENSITIVE);

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    private static final Pattern DOCKERFILE_PAT = Pattern.compile(
            "(^|\\n)\\s*(FROM|RUN|ENV|EXPOSE|WORKDIR|CMD|ENTRYPOINT|COPY|ADD|ARG|LABEL)\\s+\\S.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DOCKER_COMPOSE = Pattern.compile("(^|\\n)(services|volumes|networks):\\s*\\n");
    private static final Pattern K8S_YAML       = Pattern.compile("(^|\\n)(apiVersion|kind|metadata|spec):\\s+\\S");
    private static final Pattern MAVEN_POM      = Pattern.compile("<project[^>]*>.*<(groupId|artifactId)>", Pattern.DOTALL);
    private static final Pattern GRADLE_PAT     = Pattern.compile("(plugins\\s*\\{|dependencies\\s*\\{|implementation\\s+['\"])");
    private static final Pattern SPRING_PROPS   = Pattern.compile("(spring\\.(application|datasource|jpa|security|boot)|server\\.port)");
    private static final Pattern PROPS_LINE     = Pattern.compile("^([a-zA-Z][a-zA-Z0-9._-]*)\\s*=\\s*.+");

    // -------------------------------------------------------------------------
    // Main classify method
    // -------------------------------------------------------------------------

    public Result classify(String text) {
        if (text == null || text.isBlank()) {
            return Result.plainText();
        }
        String trimmed = text.trim();

        // 1. Sensitivity — always first
        boolean sensitive = detectSensitive(trimmed);

        // 2. JSON
        if (isJson(trimmed)) {
            return Result.of("JSON", null, List.of(), "DATA", sensitive, 1.0f);
        }

        // 3. URL
        if (URL_PAT.matcher(trimmed).matches()) {
            return Result.of("URL", null, detectUrlTechs(trimmed), "DOCUMENTATION", sensitive, 1.0f);
        }

        // 4. Email
        if (EMAIL_PAT.matcher(trimmed).matches()) {
            return Result.of("EMAIL", null, List.of(), "COMMUNICATION", sensitive, 1.0f);
        }

        // 5. UUID
        if (UUID_PAT.matcher(trimmed).matches()) {
            return Result.of("UUID", null, List.of(), "IDENTIFIER", sensitive, 0.98f);
        }

        // 6. IP address
        if (IPV4_PAT.matcher(trimmed).matches()) {
            return Result.of("IP_ADDRESS", null, List.of(), "NETWORKING", sensitive, 1.0f);
        }

        // 7. File path (single-line only)
        if (!trimmed.contains("\n") && (FILE_WIN_PAT.matcher(trimmed).matches() || FILE_UNIX_PAT.matcher(trimmed).matches())) {
            return Result.of("FILE_PATH", null, List.of(), "FILESYSTEM", sensitive, 0.9f);
        }

        // 8. Stack trace / error
        Result errResult = checkError(trimmed, sensitive);
        if (errResult != null) return errResult;

        // 9. SQL
        if (isSql(trimmed)) {
            return Result.of("SQL", "SQL", List.of("SQL"), "DATABASE", sensitive, 0.98f);
        }

        // 10. Terminal commands
        Result cmdResult = checkCommand(trimmed, sensitive);
        if (cmdResult != null) return cmdResult;

        // 11. Configuration
        Result configResult = checkConfiguration(trimmed, sensitive);
        if (configResult != null) return configResult;

        // 12. Code
        Result codeResult = checkCode(trimmed, sensitive);
        if (codeResult != null) return codeResult;

        // 13. Sensitive fallback
        if (sensitive) return Result.sensitiveText();

        return Result.plainText();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean detectSensitive(String text) {
        return JWT_PAT.matcher(text).find()
                || API_KEY_PAT.matcher(text).find()
                || PRIVATE_KEY_PAT.matcher(text).find()
                || PASSWORD_PAT.matcher(text).find();
    }

    private boolean isJson(String t) {
        if (!((t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]")))) return false;
        // Lightweight JSON validation — check balanced braces
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '"' && (i == 0 || t.charAt(i - 1) != '\\')) inString = !inString;
            if (!inString) {
                if (c == '{' || c == '[') depth++;
                else if (c == '}' || c == ']') depth--;
            }
        }
        return depth == 0;
    }

    private List<String> detectUrlTechs(String url) {
        String lower = url.toLowerCase();
        List<String> techs = new ArrayList<>();
        if (lower.contains("github.com") || lower.contains("git-scm.com")) techs.add("GIT");
        if (lower.contains("spring.io") || lower.contains("springframework"))   techs.add("SPRING_BOOT");
        if (lower.contains("docker"))        techs.add("DOCKER");
        if (lower.contains("kubernetes") || lower.contains("k8s.io")) techs.add("KUBERNETES");
        if (lower.contains("npmjs.com"))     techs.add("NODE_JS");
        if (lower.contains("amazonaws.com")) techs.add("AWS");
        return techs;
    }

    private Result checkError(String text, boolean sensitive) {
        boolean isJava = JAVA_TRACE.matcher(text).find();
        boolean isPy   = PY_TRACE.matcher(text).find();
        boolean isErr  = text.contains("FATAL:") || text.contains("ERROR:") || text.contains("FAILED:");
        if (!isJava && !isPy && !isErr) return null;
        List<String> techs = new ArrayList<>();
        String lang = null;
        String lower = text.toLowerCase();
        if (isPy || lower.contains("traceback")) { techs.add("PYTHON"); lang = "PYTHON"; }
        else if (isJava || text.contains(".java:")) {
            if (lower.contains("springframework")) techs.add("SPRING_BOOT");
            techs.add("JAVA"); lang = "JAVA";
        }
        String type = (isJava || isPy) ? "STACK_TRACE" : "ERROR_MESSAGE";
        return Result.of(type, lang, techs, "DEBUG", sensitive, 0.95f);
    }

    private boolean isSql(String text) {
        if (SQL_START.matcher(text).find()) return true;
        String u = text.toUpperCase();
        return u.contains("SELECT ") && u.contains(" FROM ")
                && (u.contains(" WHERE ") || u.contains(";") || u.contains("\n"));
    }

    private Result checkCommand(String text, boolean sensitive) {
        String first = text.lines().filter(l -> !l.isBlank()).findFirst().orElse("").trim();
        if (DOCKER_CMD.matcher(first).matches())  return Result.of("COMMAND", "SHELL", List.of("DOCKER"),     "DEVOPS", sensitive, 0.97f);
        if (GIT_CMD.matcher(first).matches())     return Result.of("COMMAND", "SHELL", List.of("GIT"),        "DEVOPS", sensitive, 0.97f);
        if (KUBECTL_CMD.matcher(first).matches()) return Result.of("COMMAND", "SHELL", List.of("KUBERNETES"), "DEVOPS", sensitive, 0.97f);
        if (AWS_CMD.matcher(first).matches())     return Result.of("COMMAND", "SHELL", List.of("AWS"),        "DEVOPS", sensitive, 0.97f);
        if (MAVEN_CMD.matcher(first).matches())   return Result.of("COMMAND", "SHELL", List.of("MAVEN"),      "BUILD",  sensitive, 0.97f);
        if (NPM_CMD.matcher(first).matches())     return Result.of("COMMAND", "SHELL", List.of("NODE_JS"),    "BUILD",  sensitive, 0.97f);
        if (PYTHON_CMD.matcher(first).matches())  return Result.of("COMMAND", "PYTHON", List.of("PYTHON"),   "DEVOPS", sensitive, 0.97f);
        if (JAVA_CMD.matcher(first).matches())    return Result.of("COMMAND", "SHELL", List.of("JAVA"),       "BUILD",  sensitive, 0.97f);
        if (PS_CMD.matcher(first).matches())      return Result.of("COMMAND", "POWERSHELL", List.of(),        "DEVOPS", sensitive, 0.9f);
        if (SHELL_CMD.matcher(first).matches())   return Result.of("COMMAND", "SHELL", List.of(),             "DEVOPS", sensitive, 0.9f);
        return null;
    }

    private Result checkConfiguration(String text, boolean sensitive) {
        if (DOCKER_COMPOSE.matcher(text).find())
            return Result.of("CONFIGURATION", "YAML", List.of("DOCKER"), "DEVOPS", sensitive, 0.95f);
        if (K8S_YAML.matcher(text).find() && text.contains("apiVersion:"))
            return Result.of("CONFIGURATION", "YAML", List.of("KUBERNETES"), "DEVOPS", sensitive, 0.95f);
        if (DOCKERFILE_PAT.matcher(text).find())
            return Result.of("CONFIGURATION", null, List.of("DOCKER"), "DEVOPS", sensitive, 0.95f);
        if (MAVEN_POM.matcher(text).find())
            return Result.of("CONFIGURATION", "XML", List.of("MAVEN"), "BUILD", sensitive, 0.95f);
        if (GRADLE_PAT.matcher(text).find())
            return Result.of("CONFIGURATION", "GROOVY", List.of("GRADLE"), "BUILD", sensitive, 0.9f);
        if (SPRING_PROPS.matcher(text).find()) {
            String lang = text.contains(":\n") || text.contains(": ") ? "YAML" : "PROPERTIES";
            return Result.of("CONFIGURATION", lang, List.of("SPRING_BOOT"), "CONFIGURATION", sensitive, 0.93f);
        }
        // Generic properties file
        long total = text.lines().filter(l -> !l.isBlank()).count();
        long props  = text.lines().filter(l -> l.isBlank() || l.trim().startsWith("#") || PROPS_LINE.matcher(l.trim()).matches()).count();
        if (total >= 2 && props == total)
            return Result.of("CONFIGURATION", "PROPERTIES", List.of(), "CONFIGURATION", sensitive, 0.75f);
        return null;
    }

    private Result checkCode(String text, boolean sensitive) {
        // TypeScript
        if (isTs(text)) {
            List<String> techs = new ArrayList<>(); techs.add("TYPESCRIPT");
            if (hasReact(text)) techs.add("REACT");
            return Result.of("CODE", "TYPESCRIPT", techs, "PROGRAMMING", sensitive, 0.93f);
        }
        // Java / Spring Boot
        if (isJava(text)) {
            List<String> techs = new ArrayList<>();
            if (isSpring(text)) techs.add("SPRING_BOOT");
            techs.add("JAVA");
            return Result.of("CODE", "JAVA", techs, "PROGRAMMING", sensitive, 0.95f);
        }
        // Kotlin
        if (isKotlin(text)) {
            List<String> techs = new ArrayList<>(); techs.add("KOTLIN");
            if (isSpring(text)) techs.add("SPRING_BOOT");
            return Result.of("CODE", "KOTLIN", techs, "PROGRAMMING", sensitive, 0.9f);
        }
        // Python
        if (isPython(text)) {
            List<String> techs = new ArrayList<>(); techs.add("PYTHON");
            return Result.of("CODE", "PYTHON", techs, "PROGRAMMING", sensitive, 0.93f);
        }
        // JavaScript
        if (isJs(text)) {
            List<String> techs = new ArrayList<>(); techs.add("JAVASCRIPT");
            if (hasReact(text)) techs.add("REACT");
            return Result.of("CODE", "JAVASCRIPT", techs, "PROGRAMMING", sensitive, 0.9f);
        }
        // Go
        if (isGo(text)) return Result.of("CODE", "GO",   List.of("GO"),   "PROGRAMMING", sensitive, 0.9f);
        // Rust
        if (isRust(text)) return Result.of("CODE", "RUST", List.of("RUST"), "PROGRAMMING", sensitive, 0.9f);
        // C#
        if (isCSharp(text)) return Result.of("CODE", "CSHARP", List.of("DOTNET"), "PROGRAMMING", sensitive, 0.9f);
        // PHP
        if (isPhp(text)) return Result.of("CODE", "PHP", List.of("PHP"), "PROGRAMMING", sensitive, 0.9f);
        // HTML
        if (isHtml(text)) {
            List<String> techs = new ArrayList<>();
            if (hasReact(text)) techs.add("REACT");
            return Result.of("CODE", "HTML", techs, "WEB", sensitive, 0.88f);
        }
        return null;
    }

    private boolean isSpring(String t) {
        return t.contains("@SpringBootApplication") || t.contains("@RestController")
                || t.contains("@GetMapping") || t.contains("@PostMapping")
                || t.contains("@Service") || t.contains("@Repository") || t.contains("@Autowired")
                || t.contains("import org.springframework.");
    }

    private boolean hasReact(String t) {
        return t.contains("import React") || t.contains("useState(") || t.contains("useEffect(")
                || t.contains("useContext(") || t.contains("React.FC") || t.contains("JSX.Element");
    }

    private boolean hasBrackets(String t) {
        return (t.contains("{") && t.contains("}")) || (t.contains("(") && t.contains(")") && t.contains(";"));
    }

    private boolean isTs(String t) {
        return (t.contains(": string") || t.contains(": number") || t.contains(": boolean")
                || t.contains(": void") || t.contains(": any") || t.contains("interface "))
                && (t.contains("const ") || t.contains("let ") || t.contains("import ") || t.contains("export "))
                && hasBrackets(t);
    }

    private boolean isJava(String t) {
        boolean standardJava = t.contains("public class ") || t.contains("private class ")
                || t.contains("protected class ") || t.contains("abstract class ")
                || t.contains("public interface ") || t.contains("public enum ")
                || t.contains("public static void main") || t.contains("System.out.println(")
                || t.contains("import java.") || t.contains("import jakarta.")
                || t.contains("@Override") || t.contains("@Entity");

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

        boolean javaInstantiations = (t.contains("new HashMap<") || t.contains("new ArrayList<")
                || t.contains("new LinkedList<") || t.contains("new HashSet<")
                || t.contains("new StringBuilder(") || t.contains("new StringBuffer("))
                && t.contains(";");

        return (standardJava || javaGenericsOrCollections || javaInstantiations)
                && hasBrackets(t);
    }

    private boolean isKotlin(String t) {
        return t.contains("fun ") && t.contains(":") && (t.contains("{") || t.contains("=>"))
                && (t.contains("val ") || t.contains("var ") || t.contains("data class ")
                || t.contains("companion object")) && !t.contains("public class ");
    }

    private boolean isPython(String t) {
        boolean hasDefColon = t.contains("def ") && t.contains("(") && t.contains(":") && t.contains("\n");
        boolean hasFromImport = (t.startsWith("from ") || t.contains("\nfrom ")) && t.contains(" import ");
        boolean looksLikeJava = t.contains("{") && t.contains("}") && t.contains(";");
        return !looksLikeJava && (hasDefColon || hasFromImport
                || (t.contains("print(") && !t.contains(";") && !t.contains("System.out")));
    }

    private boolean isJs(String t) {
        boolean hasKw = t.contains("const ") || t.contains("let ") || t.contains("var ")
                || t.contains("function ") || t.contains("console.log(") || t.contains("=> {")
                || t.contains("require(") || t.contains("module.exports");
        return hasKw && hasBrackets(t);
    }

    private boolean isGo(String t) {
        return t.contains("package ") && t.contains("import (") && t.contains("func ")
                && t.contains("{") && t.contains("}");
    }

    private boolean isRust(String t) {
        return t.contains("fn ") && t.contains("->") && t.contains("{")
                && (t.contains("let mut ") || t.contains("impl ") || t.contains("use std::") || t.contains("pub fn"));
    }

    private boolean isCSharp(String t) {
        return (t.contains("using System") || t.contains("namespace ") || t.contains("static void Main")
                || t.contains("Console.WriteLine")) && t.contains("{") && t.contains("}") && t.contains(";");
    }

    private boolean isPhp(String t) {
        return t.contains("<?php") || (t.contains("$") && t.contains("->") && t.contains(";")
                && (t.contains("function ") || t.contains("class ")));
    }

    private boolean isHtml(String t) {
        return (t.contains("<!DOCTYPE html") || t.contains("<html") || t.contains("<head>")
                || t.contains("<body>") || t.contains("<div")) && t.contains(">") && t.contains("</");
    }
}

