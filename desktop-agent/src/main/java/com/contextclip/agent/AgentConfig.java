package com.contextclip.agent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Handles hierarchical configuration resolution for the Desktop Agent.
 * Priority:
 * 1. Environment variable AGENT_TOKEN
 * 2. Environment variable DESKTOP_AGENT_TOKEN
 * 3. User config file: ~/.contextclip/agent.properties
 * 4. Local directory config file: ./agent.properties
 */
public class AgentConfig {

    public static final String DEFAULT_ENDPOINT_URL = "http://localhost:8080/api/clipboard";
    public static final String DEFAULT_AUTH_URL     = "http://localhost:8080/api/auth/agent-login";

    private final String agentToken;
    private final String endpointUrl;
    private final String authUrl;
    private final String tokenSource;

    public AgentConfig(String agentToken, String endpointUrl, String authUrl, String tokenSource) {
        this.agentToken = agentToken != null ? agentToken.trim() : null;
        this.endpointUrl = (endpointUrl != null && !endpointUrl.isBlank()) ? endpointUrl.trim() : DEFAULT_ENDPOINT_URL;
        this.authUrl = (authUrl != null && !authUrl.isBlank()) ? authUrl.trim() : DEFAULT_AUTH_URL;
        this.tokenSource = tokenSource;
    }

    public static AgentConfig load() {
        String token = null;
        String source = null;

        // 1. Check AGENT_TOKEN env var
        String envToken = System.getenv("AGENT_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            token = envToken.trim();
            source = "environment variable AGENT_TOKEN";
        }

        // 2. Check DESKTOP_AGENT_TOKEN env var
        if (token == null) {
            String desktopEnvToken = System.getenv("DESKTOP_AGENT_TOKEN");
            if (desktopEnvToken != null && !desktopEnvToken.isBlank()) {
                token = desktopEnvToken.trim();
                source = "environment variable DESKTOP_AGENT_TOKEN";
            }
        }

        // 3. Check user home config: ~/.contextclip/agent.properties
        Properties userProps = loadPropertiesFile(getUserConfigFile());
        if (token == null && userProps != null) {
            String propToken = userProps.getProperty("agent.token");
            if (propToken == null || propToken.isBlank()) {
                propToken = userProps.getProperty("AGENT_TOKEN");
            }
            if (propToken != null && !propToken.isBlank()) {
                token = stripQuotes(propToken.trim());
                source = "user config file (" + getUserConfigFile().getAbsolutePath() + ")";
            }
        }

        // 4. Check local ./agent.properties
        Properties localProps = loadPropertiesFile(new File("agent.properties"));
        if (token == null && localProps != null) {
            String propToken = localProps.getProperty("agent.token");
            if (propToken == null || propToken.isBlank()) {
                propToken = localProps.getProperty("AGENT_TOKEN");
            }
            if (propToken != null && !propToken.isBlank()) {
                token = stripQuotes(propToken.trim());
                source = "local agent.properties";
            }
        }

        // Resolve endpoint URLs
        String endpointUrl = System.getenv("DESKTOP_AGENT_API_URL");
        if (endpointUrl == null || endpointUrl.isBlank()) {
            if (userProps != null && userProps.getProperty("api.url") != null) {
                endpointUrl = userProps.getProperty("api.url");
            } else if (localProps != null && localProps.getProperty("api.url") != null) {
                endpointUrl = localProps.getProperty("api.url");
            }
        }
        if (endpointUrl == null || endpointUrl.isBlank()) {
            endpointUrl = DEFAULT_ENDPOINT_URL;
        }

        String authUrl = System.getenv("DESKTOP_AGENT_AUTH_URL");
        if (authUrl == null || authUrl.isBlank()) {
            if (userProps != null && userProps.getProperty("auth.url") != null) {
                authUrl = userProps.getProperty("auth.url");
            } else if (localProps != null && localProps.getProperty("auth.url") != null) {
                authUrl = localProps.getProperty("auth.url");
            }
        }
        if (authUrl == null || authUrl.isBlank()) {
            authUrl = DEFAULT_AUTH_URL;
        }

        return new AgentConfig(token, endpointUrl, authUrl, source);
    }

    // Testing hook to isolate tests from production user config
    static volatile File testUserConfigFile = null;

    public static void setTestUserConfigFile(File file) {
        testUserConfigFile = file;
    }

    public static File getUserConfigFile() {
        if (testUserConfigFile != null) {
            return testUserConfigFile;
        }
        String userHome = System.getProperty("user.home", ".");
        return Paths.get(userHome, ".contextclip", "agent.properties").toFile();
    }

    /**
     * Strips surrounding single or double quotation marks from a token value.
     * This handles the case where users manually set the token with quotes in agent.properties.
     */
    static String stripQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /**
     * Validates whether a token string is syntactically a complete, non-truncated 3-part JWT.
     * Rejects tokens containing ellipses ("..."), whitespace, newlines, or not having exactly 2 dots.
     */
    public static boolean isJwtStructured(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        if (token.contains("...") || token.contains("…")) {
            return false;
        }
        // A valid JWT cannot contain inner spaces or newlines
        if (token.contains(" ") || token.contains("\t") || token.contains("\n") || token.contains("\r")) {
            return false;
        }
        long dotCount = token.chars().filter(c -> c == '.').count();
        if (dotCount != 2) {
            return false;
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            return false;
        }
        // Each part (header, payload, signature) must be non-empty
        return !parts[0].isBlank() && !parts[1].isBlank() && !parts[2].isBlank();
    }

    /**
     * Safely decodes claims from JWT payload without validating signature.
     * Returns a simple record of subject and role if present.
     */
    public static class JwtClaimsSummary {
        public final String subject;
        public final String role;
        public final int partsCount;
        public final int tokenLength;

        public JwtClaimsSummary(String subject, String role, int partsCount, int tokenLength) {
            this.subject = subject;
            this.role = role;
            this.partsCount = partsCount;
            this.tokenLength = tokenLength;
        }
    }

    public static JwtClaimsSummary inspectTokenMetadata(String token) {
        if (token == null) {
            return new JwtClaimsSummary(null, null, 0, 0);
        }
        String cleaned = stripQuotes(token.trim());
        int length = cleaned.length();
        String[] parts = cleaned.split("\\.", -1);
        int partsCount = parts.length;

        if (partsCount < 2 || parts[1].isBlank()) {
            return new JwtClaimsSummary(null, null, partsCount, length);
        }

        String sub = null;
        String role = null;
        try {
            String payloadBase64 = parts[1];
            // Base64URL decoding with padding handling
            int pad = payloadBase64.length() % 4;
            if (pad == 2) payloadBase64 += "==";
            else if (pad == 3) payloadBase64 += "=";
            payloadBase64 = payloadBase64.replace('-', '+').replace('_', '/');
            byte[] decodedBytes = java.util.Base64.getDecoder().decode(payloadBase64);
            String json = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);

            // Simple safe regex extraction to avoid external JSON parser dependencies
            java.util.regex.Matcher subMatcher = java.util.regex.Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            if (subMatcher.find()) {
                sub = subMatcher.group(1);
            }
            java.util.regex.Matcher roleMatcher = java.util.regex.Pattern.compile("\"role\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            if (roleMatcher.find()) {
                role = roleMatcher.group(1);
            }
        } catch (Exception ignored) {
        }

        return new JwtClaimsSummary(sub, role, partsCount, length);
    }

    public static boolean saveUserToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            File file = getUserConfigFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Properties props = new Properties();
            if (file.exists()) {
                try (FileInputStream in = new FileInputStream(file)) {
                    props.load(in);
                } catch (Exception ignored) {
                }
            }
            // Always save without quotes and trimmed
            props.setProperty("agent.token", stripQuotes(token.trim()));
            try (FileOutputStream out = new FileOutputStream(file)) {
                props.store(out, "ContextClip Desktop Agent Configuration");
            }
            return true;
        } catch (Exception e) {
            System.err.println("Failed to save token to user configuration: " + e.getMessage());
            return false;
        }
    }

    private static Properties loadPropertiesFile(File file) {
        if (file != null && file.exists() && file.isFile()) {
            Properties props = new Properties();
            try (FileInputStream in = new FileInputStream(file)) {
                props.load(in);
                return props;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public String getAgentToken() {
        return agentToken;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public String getAuthUrl() {
        return authUrl;
    }

    public String getTokenSource() {
        return tokenSource;
    }

    public boolean hasValidToken() {
        return agentToken != null && isJwtStructured(agentToken);
    }
}
