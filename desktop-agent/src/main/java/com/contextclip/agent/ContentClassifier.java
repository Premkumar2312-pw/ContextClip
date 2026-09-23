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
    // URL / Email / UUID / IP / MAC / Hash / File path
    // -------------------------------------------------------------------------

    private static final Pattern URL_PAT = Pattern.compile(
            "^https?://\\S+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PAT = Pattern.compile(
            "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern UUID_PAT = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern IPV4_PAT = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)(:\\d{1,5})?$");
    private static final Pattern IPV6_PAT = Pattern.compile(
            "^(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|::([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}|fe80::[0-9a-fA-F:]+)$");
    private static final Pattern MAC_PAT = Pattern.compile(
            "^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$");
    private static final Pattern HASH_PAT = Pattern.compile(
            "^([0-9a-fA-F]{32}|[0-9a-fA-F]{40}|[0-9a-fA-F]{64})$");
    private static final Pattern FILE_WIN_PAT = Pattern.compile("^[A-Za-z]:\\\\(\\S+\\\\)*\\S*$");
    private static final Pattern FILE_UNIX_PAT = Pattern.compile("^(/[^/\\s]+)+/?$");
    private static final Pattern PHONE_PAT = Pattern.compile(
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

    private static final Pattern SQL_START = Pattern.compile(
            "^\\s*(SELECT|INSERT\\s+INTO|UPDATE\\s+\\w+\\s+SET|DELETE\\s+FROM|CREATE\\s+(TABLE|DATABASE)|ALTER\\s+TABLE|DROP\\s+(TABLE|DATABASE)|TRUNCATE\\s+TABLE)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // -------------------------------------------------------------------------
    // Stack trace / error
    // -------------------------------------------------------------------------

    private static final Pattern JAVA_TRACE = Pattern.compile(
            "\\bat\\s+[a-zA-Z0-9_.$]+\\([a-zA-Z0-9_]+(\\.(java|kt))?:\\d+\\)");
    private static final Pattern PY_TRACE = Pattern.compile("Traceback \\(most recent call last\\):");
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

    private static final Pattern DOCKER_CMD  = Pattern.compile("^(docker\\s+compose|docker-compose|docker|podman)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern GIT_CMD     = Pattern.compile("^git\\s+\\S.*",   Pattern.CASE_INSENSITIVE);
    private static final Pattern MAVEN_CMD   = Pattern.compile("^(\\./)?(mvn|mvnw)\\s+.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern GRADLE_CMD  = Pattern.compile("^(\\./)?(gradle|gradlew)\\s+.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern NODE_CMD    = Pattern.compile("^(node|nodejs|npm|npx|yarn|pnpm|bun|deno)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PYTHON_CMD  = Pattern.compile("^(python[23]?|py|pip[23]?|pytest|poetry|uv|pipenv|conda)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVA_CMD    = Pattern.compile("^(java|javac|javadoc|jar)\\s+.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUST_CMD    = Pattern.compile("^cargo\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern GO_CMD      = Pattern.compile("^go\\s+(run|build|test|get|mod|install|fmt|vet)\\s+.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOTNET_CMD  = Pattern.compile("^dotnet\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPOSER_CMD = Pattern.compile("^composer\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern RUBY_CMD    = Pattern.compile("^(gem|bundle|rake|ruby)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern KUBECTL_CMD = Pattern.compile("^(kubectl|helm)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern AWS_CMD     = Pattern.compile("^aws\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PKG_MGR_CMD = Pattern.compile("^(brew|apt|apt-get|yum|dnf|pacman|winget|choco)\\s+\\S.*", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHELL_CMD   = Pattern.compile(
            "^(cd|ls|dir|cat|echo|curl|wget|sudo|chmod|chown|mkdir|rmdir|rm|ps|kill|top|htop|df|du|export|set|setx|grep|find|sed|awk|tail|head|ssh|scp|cp|mv|touch|ping|traceroute|netstat|ifconfig|ip|systemctl|journalctl|service|cls|clear|tasklist|taskkill|ipconfig)\\s.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PS_CMD      = Pattern.compile(
            "^(Get-|Set-|New-|Remove-|Invoke-|Start-|Stop-|Write-|Read-|Import-|Export-|Add-|Test-|Update-|Install-|Uninstall-|powershell|pwsh)\\b.*",
            Pattern.CASE_INSENSITIVE);

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    private static final Pattern DOCKERFILE_PAT = Pattern.compile(
            "(^|\\n)\\s*(FROM|RUN|ENV|EXPOSE|WORKDIR|CMD|ENTRYPOINT|COPY|ADD|ARG|LABEL|VOLUME|USER|HEALTHCHECK|ONBUILD)\\s+\\S.*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DOCKER_COMPOSE = Pattern.compile("(^|\\n)(services|volumes|networks):\\s*\\n");
    private static final Pattern K8S_YAML       = Pattern.compile("(^|\\n)(apiVersion|kind|metadata|spec):\\s+\\S");
    private static final Pattern MAVEN_POM      = Pattern.compile("<project[^>]*>.*<(groupId|artifactId|version)>", Pattern.DOTALL);
    private static final Pattern GRADLE_PAT     = Pattern.compile("(plugins\\s*\\{|dependencies\\s*\\{|repositories\\s*\\{|implementation\\s+['\"])");
    private static final Pattern SPRING_PROPS   = Pattern.compile("(spring\\.(application|datasource|jpa|security|boot)|server\\.port|logging\\.level)");
    private static final Pattern PROPS_LINE     = Pattern.compile("^([a-zA-Z][a-zA-Z0-9._-]*)\\s*=\\s*.+");
    private static final Pattern TOML_PAT       = Pattern.compile("(^|\\n)\\s*\\[[a-zA-Z0-9._-]+\\]\\s*\\n");
    private static final Pattern MAKEFILE_PAT   = Pattern.compile("(^|\\n)(\\.PHONY:|[a-zA-Z0-9._-]+:.*\\n\\t\\S+)");

    // -------------------------------------------------------------------------
    // Main classify method
    // -------------------------------------------------------------------------

    public Result classify(String text) {
        if (text == null || text.isBlank()) {
            return Result.plainText();
        }
        String trimmed = text.trim();

        // 0. Image Data URL
        if (trimmed.startsWith("data:image/")) {
            return Result.of("IMAGE", "IMAGE", List.of("IMAGE"), "IMAGE", false, 1.0f);
        }

        // 1. Sensitivity — always first
        boolean sensitive = detectSensitive(trimmed);

        // 2. Phone number
        if (isPhoneNumber(trimmed)) {
            return Result.of("PHONE_NUMBER", null, List.of(), "COMMUNICATION", sensitive, 0.95f);
        }

        // 3. Email
        if (EMAIL_PAT.matcher(trimmed).matches()) {
            return Result.of("EMAIL", null, List.of(), "COMMUNICATION", sensitive, 1.0f);
        }

        // 4. URL
        if (URL_PAT.matcher(trimmed).matches()) {
            return Result.of("URL", null, detectUrlTechs(trimmed), "DOCUMENTATION", sensitive, 1.0f);
        }

        // 5. Network & Identifiers (UUID, IPv4, IPv6, MAC, Hash)
        if (UUID_PAT.matcher(trimmed).matches()) {
            return Result.of("UUID", null, List.of(), "IDENTIFIER", sensitive, 0.98f);
        }
        if (IPV4_PAT.matcher(trimmed).matches()) {
            return Result.of("IP_ADDRESS", null, List.of(), "NETWORKING", sensitive, 1.0f);
        }
        if (IPV6_PAT.matcher(trimmed).matches()) {
            return Result.of("IP_ADDRESS", null, List.of(), "NETWORKING", sensitive, 0.98f);
        }
        if (MAC_PAT.matcher(trimmed).matches()) {
            return Result.of("MAC_ADDRESS", null, List.of(), "NETWORKING", sensitive, 0.98f);
        }
        if (HASH_PAT.matcher(trimmed).matches()) {
            return Result.of("HASH", null, List.of(), "IDENTIFIER", sensitive, 0.95f);
        }

        // 6. Stack trace / error
        Result errResult = checkError(trimmed, sensitive);
        if (errResult != null) return errResult;

        // 7. Mathematical / Scientific formulas
        Result mathResult = checkMath(trimmed, sensitive);
        if (mathResult != null) return mathResult;

        // 8. SQL
        if (isSql(trimmed)) {
            return Result.of("SQL", "SQL", List.of("SQL"), "DATABASE", sensitive, 0.98f);
        }

        // 9. Terminal commands
        Result cmdResult = checkCommand(trimmed, sensitive);
        if (cmdResult != null) return cmdResult;

        // 10. Code
        Result codeResult = checkCode(trimmed, sensitive);
        if (codeResult != null) return codeResult;

        // 11. Configuration
        Result configResult = checkConfiguration(trimmed, sensitive);
        if (configResult != null) return configResult;

        // 12. Data formats
        if (isJson(trimmed)) {
            return Result.of("JSON", null, List.of(), "DATA", sensitive, 1.0f);
        }

        // File path (single-line only)
        if (!trimmed.contains("\n") && (FILE_WIN_PAT.matcher(trimmed).matches() || FILE_UNIX_PAT.matcher(trimmed).matches())) {
            return Result.of("FILE_PATH", null, List.of(), "FILESYSTEM", sensitive, 0.9f);
        }

        // 13. Multilingual natural text
        Result multiResult = checkMultilingual(trimmed, sensitive);
        if (multiResult != null) return multiResult;

        // 14. Sensitive fallback
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
        boolean isJavaTrace       = JAVA_TRACE.matcher(text).find();
        boolean isPyTrace         = PY_TRACE.matcher(text).find();
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
        String lang = null;
        String lower = text.toLowerCase();

        if (isPyTrace || isPyException || lower.contains("traceback") || text.contains(".py\"") || lower.contains("modulenotfounderror")) {
            techs.add("PYTHON"); lang = "PYTHON";
        } else if (isJavaTrace || isJavaException || lower.contains("springframework") || text.contains(".java:") || text.contains("javac:")) {
            if (lower.contains("springframework") || lower.contains("spring")) techs.add("SPRING_BOOT");
            techs.add("JAVA"); lang = "JAVA";
        } else if (lower.contains("npm err") || lower.contains("npm error") || lower.contains("eresolve")) {
            techs.add("NODE_JS"); lang = "JAVASCRIPT";
        } else if (lower.contains("error ts") || lower.contains("type '") && lower.contains("' is not assignable to type")) {
            techs.add("TYPESCRIPT"); lang = "TYPESCRIPT";
        } else if (lower.contains("error[e") || lower.contains("rustc")) {
            techs.add("RUST"); lang = "RUST";
        } else if (lower.contains("react") || lower.contains(".tsx:") || lower.contains(".jsx:")) {
            techs.add("REACT"); lang = "TYPESCRIPT";
        } else if (lower.contains("node") || lower.contains(".js:")) {
            techs.add("NODE_JS"); lang = "JAVASCRIPT";
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
        return Result.of(type, lang, techs, cats, sensitive, 0.95f);
    }

    private Result checkMath(String text, boolean sensitive) {
        boolean hasLatex = LATEX_MATH_COMMAND.matcher(text).find();
        boolean hasUnicodeMath = UNICODE_MATH_SYMBOLS.matcher(text).find();
        boolean hasGreek = GREEK_MATH_LETTERS.matcher(text).find();
        boolean hasScripts = MATH_SUPERSCRIPTS_SUBSCRIPTS.matcher(text).find();

        if (!hasLatex && !hasUnicodeMath && !(hasGreek && (hasScripts || text.contains("=") || text.contains("^")))) {
            return null;
        }

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

        boolean isNavierStokes = (text.contains("\\nabla") || text.contains("∇") || text.contains("nabla"))
                && (text.contains("\\partial") || text.contains("∂"))
                && (text.contains("\\rho") || text.contains("ρ") || text.contains("\\nu") || text.contains("ν") || lower.contains("navier") || text.contains("∇²") || text.contains("\\nabla^2"));

        boolean isSchrodinger = (text.contains("Ψ") || text.contains("\\psi") || text.contains("\\Psi") || lower.contains("schrodinger") || lower.contains("schrödinger"))
                && (text.contains("ℏ") || text.contains("\\hbar") || text.contains("Ĥ") || text.contains("\\hat{H}") || text.contains("iℏ"));

        boolean isMaxwell = (text.contains("∇ ·") || text.contains("\\nabla \\cdot") || text.contains("∇ ×") || text.contains("\\nabla \\times") || lower.contains("maxwell"))
                && (text.contains("E") || text.contains("B") || text.contains("D") || text.contains("H"));

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
            return Result.of("FORMULA", "LATEX", techs, cats, sensitive, 0.96f);
        } else {
            if (techs.isEmpty()) techs.add("MATHEMATICS");
            return Result.of("FORMULA", "UNICODE_MATH", techs, cats, sensitive, 0.95f);
        }
    }

    private Result checkMultilingual(String text, boolean sensitive) {
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
            return Result.of("PLAIN_TEXT", lang, List.of(lang), List.of("COMMUNICATION", "GENERAL"), sensitive, 0.95f);
        }

        String lower = " " + text.toLowerCase() + " ";
        if (text.matches(".*[éèêàçùôî].*") || (lower.contains(" le ") && lower.contains(" la ") && lower.contains(" et "))
                || lower.contains(" c'est ") || lower.contains(" dans le ")) {
            return Result.of("PLAIN_TEXT", "FRENCH", List.of("FRENCH"), List.of("COMMUNICATION", "GENERAL"), sensitive, 0.85f);
        }
        if (text.matches(".*[äöüß].*") || (lower.contains(" der ") && lower.contains(" die ") && lower.contains(" und "))
                || lower.contains(" nicht ") || lower.contains(" das ist ")) {
            return Result.of("PLAIN_TEXT", "GERMAN", List.of("GERMAN"), List.of("COMMUNICATION", "GENERAL"), sensitive, 0.85f);
        }
        if (text.matches(".*[ñáíóú¿¡].*") || (lower.contains(" el ") && lower.contains(" la ") && lower.contains(" y "))
                || lower.contains(" por favor ") || lower.contains(" de la ")) {
            return Result.of("PLAIN_TEXT", "SPANISH", List.of("SPANISH"), List.of("COMMUNICATION", "GENERAL"), sensitive, 0.85f);
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
        if (KUBECTL_CMD.matcher(first).matches()) {
            List<String> techs = first.toLowerCase().startsWith("helm")
                    ? List.of("KUBERNETES", "HELM")
                    : List.of("KUBERNETES");
            return Result.of("COMMAND", "SHELL", techs, "DEVOPS", sensitive, 0.97f);
        }
        if (AWS_CMD.matcher(first).matches())     return Result.of("COMMAND", "SHELL", List.of("AWS"),        "DEVOPS", sensitive, 0.97f);
        if (MAVEN_CMD.matcher(first).matches())   return Result.of("COMMAND", "SHELL", List.of("MAVEN"),      "BUILD",  sensitive, 0.97f);
        if (GRADLE_CMD.matcher(first).matches())  return Result.of("COMMAND", "SHELL", List.of("GRADLE"),     List.of("DEVOPS", "BUILD"), sensitive, 0.97f);
        if (NODE_CMD.matcher(first).matches())    return Result.of("COMMAND", "SHELL", List.of("NODE_JS"),    List.of("DEVOPS", "BUILD"), sensitive, 0.97f);
        if (PYTHON_CMD.matcher(first).matches())  return Result.of("COMMAND", "PYTHON", List.of("PYTHON"),   "DEVOPS", sensitive, 0.97f);
        if (RUST_CMD.matcher(first).matches())    return Result.of("COMMAND", "SHELL", List.of("RUST", "CARGO"), List.of("BUILD", "DEVOPS"), sensitive, 0.97f);
        if (GO_CMD.matcher(first).matches())      return Result.of("COMMAND", "SHELL", List.of("GO"), List.of("BUILD", "DEVOPS"), sensitive, 0.97f);
        if (DOTNET_CMD.matcher(first).matches())  return Result.of("COMMAND", "SHELL", List.of("DOTNET"), List.of("BUILD", "DEVOPS"), sensitive, 0.97f);
        if (COMPOSER_CMD.matcher(first).matches()) return Result.of("COMMAND", "SHELL", List.of("PHP", "COMPOSER"), List.of("BUILD", "DEVOPS"), sensitive, 0.97f);
        if (RUBY_CMD.matcher(first).matches())    return Result.of("COMMAND", "SHELL", List.of("RUBY"), List.of("BUILD", "DEVOPS"), sensitive, 0.97f);
        if (JAVA_CMD.matcher(first).matches())    return Result.of("COMMAND", "SHELL", List.of("JAVA"),       "BUILD",  sensitive, 0.97f);
        if (PKG_MGR_CMD.matcher(first).matches()) return Result.of("COMMAND", "SHELL", List.of("SHELL"), List.of("DEVOPS", "SYSTEM"), sensitive, 0.92f);
        if (PS_CMD.matcher(first).matches())      return Result.of("COMMAND", "POWERSHELL", List.of("POWERSHELL"), "DEVOPS", sensitive, 0.9f);
        if (SHELL_CMD.matcher(first).matches())   return Result.of("COMMAND", "SHELL",      List.of("SHELL"),      "DEVOPS", sensitive, 0.9f);
        return null;
    }

    private boolean isPhoneNumber(String text) {
        if (text.contains("\n") || text.length() < 7 || text.length() > 25) {
            return false;
        }
        if (IPV4_PAT.matcher(text).matches()) {
            return false;
        }
        long digits = text.chars().filter(Character::isDigit).count();
        return digits >= 7 && digits <= 15 && PHONE_PAT.matcher(text).matches();
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
        if (TOML_PAT.matcher(text).find() && (text.contains("=") || text.contains("\""))) {
            return Result.of("CONFIGURATION", "TOML", List.of("TOML"), "CONFIGURATION", sensitive, 0.9f);
        }
        if (MAKEFILE_PAT.matcher(text).find() && text.contains("\t")) {
            return Result.of("CONFIGURATION", "MAKEFILE", List.of("MAKEFILE"), List.of("BUILD", "CONFIGURATION"), sensitive, 0.9f);
        }
        long total = text.lines().filter(l -> !l.isBlank()).count();
        long props  = text.lines().filter(l -> l.isBlank() || l.trim().startsWith("#") || PROPS_LINE.matcher(l.trim()).matches()).count();
        if (total >= 2 && props == total)
            return Result.of("CONFIGURATION", "PROPERTIES", List.of(), "CONFIGURATION", sensitive, 0.75f);
        return null;
    }

    private Result checkCode(String text, boolean sensitive) {
        // C++ (before C)
        if (isCpp(text)) {
            return Result.of("CODE", "CPP", List.of("CPP"), "PROGRAMMING", sensitive, 0.93f);
        }
        // C
        if (isC(text)) {
            return Result.of("CODE", "C", List.of("C"), "PROGRAMMING", sensitive, 0.92f);
        }
        // TypeScript
        if (isTs(text)) {
            List<String> techs = new ArrayList<>(); techs.add("TYPESCRIPT");
            if (hasReact(text)) techs.add("REACT");
            return Result.of("CODE", "TYPESCRIPT", techs, "PROGRAMMING", sensitive, 0.93f);
        }
        // C#
        if (isCSharp(text)) return Result.of("CODE", "CSHARP", List.of("DOTNET", "CSHARP"), "PROGRAMMING", sensitive, 0.93f);
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
        // Swift
        if (isSwift(text)) {
            return Result.of("CODE", "SWIFT", List.of("SWIFT"), "PROGRAMMING", sensitive, 0.92f);
        }
        // Python
        if (isPython(text)) {
            List<String> techs = new ArrayList<>();
            techs.add("PYTHON");
            if (text.contains("pandas") || text.contains("pd.")) techs.add("PANDAS");
            if (text.contains("numpy") || text.contains("np.")) techs.add("NUMPY");
            return Result.of("CODE", "PYTHON", techs, "PROGRAMMING", sensitive, 0.93f);
        }
        // Go
        if (isGo(text)) return Result.of("CODE", "GO",   List.of("GO"),   "PROGRAMMING", sensitive, 0.9f);
        // Rust
        if (isRust(text)) return Result.of("CODE", "RUST", List.of("RUST"), "PROGRAMMING", sensitive, 0.9f);
        // Dart
        if (isDart(text)) return Result.of("CODE", "DART", List.of("DART"), "PROGRAMMING", sensitive, 0.9f);
        // Ruby
        if (isRuby(text)) return Result.of("CODE", "RUBY", List.of("RUBY"), "PROGRAMMING", sensitive, 0.9f);
        // PHP
        if (isPhp(text)) return Result.of("CODE", "PHP", List.of("PHP"), "PROGRAMMING", sensitive, 0.9f);
        // JavaScript
        if (isJs(text)) {
            List<String> techs = new ArrayList<>(); techs.add("JAVASCRIPT");
            if (hasReact(text)) techs.add("REACT");
            return Result.of("CODE", "JAVASCRIPT", techs, "PROGRAMMING", sensitive, 0.9f);
        }
        // Scala
        if (isScala(text)) return Result.of("CODE", "SCALA", List.of("SCALA"), "PROGRAMMING", sensitive, 0.9f);
        // R
        if (isR(text)) return Result.of("CODE", "R", List.of("R"), "PROGRAMMING", sensitive, 0.88f);
        // MATLAB
        if (isMatlab(text)) return Result.of("CODE", "MATLAB", List.of("MATLAB"), "PROGRAMMING", sensitive, 0.88f);
        // Perl
        if (isPerl(text)) return Result.of("CODE", "PERL", List.of("PERL"), "PROGRAMMING", sensitive, 0.88f);
        // Lua
        if (isLua(text)) return Result.of("CODE", "LUA", List.of("LUA"), "PROGRAMMING", sensitive, 0.88f);
        // HTML
        if (isHtml(text)) {
            List<String> techs = new ArrayList<>();
            if (hasReact(text)) techs.add("REACT");
            return Result.of("CODE", "HTML", techs, "WEB", sensitive, 0.88f);
        }
        // CSS
        if (isCss(text)) {
            return Result.of("CODE", "CSS", List.of(), "WEB", sensitive, 0.88f);
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
        return hasTsType && hasJsDecl && hasBrackets(t);
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
                && hasBrackets(t);
    }

    private boolean isKotlin(String t) {
        return t.contains("fun ") && t.contains(":") && (t.contains("{") || t.contains("=>"))
                && (t.contains("val ") || t.contains("var ") || t.contains("data class ")
                || t.contains("companion object")) && !t.contains("public class ");
    }

    private boolean isSwift(String t) {
        return (t.contains("import UIKit") || t.contains("import SwiftUI") || t.contains("import Foundation"))
                || ((t.contains("func ") || t.contains("struct ")) && (t.contains("@State") || t.contains("@Binding") || t.contains(": View") || t.contains("guard let ")))
                && hasBrackets(t);
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
        boolean hasPandasOrNumpy = (t.contains("pd.") || t.contains("np."))
                && (t.contains("read_csv") || t.contains("DataFrame") || t.contains("Series")
                    || t.contains("array(") || t.contains("zeros(") || t.contains("plot("));

        return hasDefColon || hasPythonImport || (hasPyClass && !t.contains("{")) || hasPrint || hasMainGuard || hasPandasOrNumpy;
    }

    private boolean isJs(String t) {
        if (isRust(t) || isDart(t)) {
            return false;
        }
        boolean hasKw = t.contains("const ") || t.contains("let ") || t.contains("var ")
                || t.contains("function ") || t.contains("console.log(") || t.contains("=> {")
                || t.contains("require(") || t.contains("module.exports");
        return hasKw && hasBrackets(t);
    }

    private boolean isCpp(String t) {
        boolean cppHeaders = t.contains("#include <iostream>") || t.contains("#include <vector>")
                || t.contains("#include <string>") || t.contains("#include <map>") || t.contains("#include <memory>");
        boolean cppSyntax = t.contains("std::cout") || t.contains("std::vector") || t.contains("std::string")
                || t.contains("std::endl") || t.contains("std::cin") || t.contains("nullptr")
                || t.contains("template <") || t.contains("template<") || (t.contains("namespace ") && t.contains("std"));
        return (cppHeaders || cppSyntax) && hasBrackets(t);
    }

    private boolean isC(String t) {
        boolean cHeaders = t.contains("#include <stdio.h>") || t.contains("#include <stdlib.h>")
                || t.contains("#include <string.h>") || t.contains("#include <unistd.h>");
        boolean cKeywords = (t.contains("printf(") || t.contains("malloc(") || t.contains("free(") || t.contains("sizeof("))
                && (t.contains("int main(") || t.contains("void main("))
                && t.contains(";");
        return (cHeaders || cKeywords) && hasBrackets(t) && !isCpp(t);
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
                && hasBrackets(t);
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
        return (t.contains("<!DOCTYPE html") || t.contains("<html") || t.contains("<head>")
                || t.contains("<body>") || t.contains("<div")) && t.contains(">") && t.contains("</");
    }

    private boolean isCss(String t) {
        return t.matches("(?s).*[.#]?[a-zA-Z][a-zA-Z0-9_-]*\\s*\\{[^}]+\\}.*")
                && (t.contains("px") || t.contains("em") || t.contains("color") || t.contains("margin")
                || t.contains("padding") || t.contains("font") || t.contains("display:"));
    }
}
