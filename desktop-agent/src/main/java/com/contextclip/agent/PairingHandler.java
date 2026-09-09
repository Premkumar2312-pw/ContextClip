package com.contextclip.agent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles incoming pairing requests via the contextclip:// URI protocol or CLI.
 * Exchanges single-use pairing codes with the backend and saves the resulting AGENT_TOKEN.
 */
public class PairingHandler {

    private static final Pattern CODE_PARAM_PATTERN = Pattern.compile("code=([^&\\s\"'#]+)");
    private static final Pattern TOKEN_JSON_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern USERNAME_JSON_PATTERN = Pattern.compile("\"username\"\\s*:\\s*\"([^\"]+)\"");

    private final HttpClient httpClient;

    public record PairingResult(
        boolean success,
        String username,
        String message
    ) {}

    public PairingHandler() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public PairingHandler(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Extracts the pairing code from either a contextclip:// URI or a raw code string.
     * Handles Windows URI protocol normalization (e.g. contextclip://pair/?code=...)
     * and quoted arguments.
     */
    public static String extractCode(String uriOrCode) {
        if (uriOrCode == null || uriOrCode.isBlank()) {
            return null;
        }
        String trimmed = uriOrCode.trim();
        while ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
               (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            if (trimmed.length() < 2) break;
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.startsWith("contextclip://") || trimmed.contains("code=")) {
            Matcher matcher = CODE_PARAM_PATTERN.matcher(trimmed);
            if (matcher.find()) {
                String code = matcher.group(1);
                while (code.endsWith("\"") || code.endsWith("'") || code.endsWith("/")) {
                    code = code.substring(0, code.length() - 1);
                }
                return code;
            }
        }
        // Fallback: direct code passed
        while (trimmed.endsWith("\"") || trimmed.endsWith("'") || trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Executes the pairing flow:
     * 1. Extracts & validates pairing code syntax.
     * 2. Calls POST /api/agent/pairing/exchange with the one-time code.
     * 3. Extracts returned AGENT_TOKEN and saves it to user configuration.
     * 4. Returns PairingResult without exposing secrets.
     */
    public PairingResult handlePairing(String uriOrCode, String apiEndpointUrl) {
        String code = extractCode(uriOrCode);
        if (code == null || code.isBlank() || !code.startsWith("pair_")) {
            return new PairingResult(false, null, "Invalid pairing code or URI format. Code must begin with 'pair_'.");
        }

        String exchangeUrl = deriveExchangeUrl(apiEndpointUrl);

        try {
            String jsonPayload = "{\"code\":\"" + BackendClient.escapeJson(code) + "\"}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(exchangeUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Matcher tokenMatcher = TOKEN_JSON_PATTERN.matcher(response.body());
                Matcher userMatcher = USERNAME_JSON_PATTERN.matcher(response.body());

                if (tokenMatcher.find()) {
                    String token = tokenMatcher.group(1);
                    String username = userMatcher.find() ? userMatcher.group(1) : "unknown";

                    if (!AgentConfig.isJwtStructured(token)) {
                        return new PairingResult(false, null, "Backend returned an improperly formatted token.");
                    }

                    boolean saved = AgentConfig.saveUserToken(token);
                    if (!saved) {
                        return new PairingResult(false, username, "Failed to save paired token to local user configuration.");
                    }

                    return new PairingResult(true, username, "Desktop agent successfully paired for user: " + username);
                }
            } else if (response.statusCode() == 401 || response.statusCode() == 400) {
                return new PairingResult(false, null, "Pairing code has expired or has already been used. Please generate a new code.");
            } else {
                return new PairingResult(false, null, "Backend returned HTTP " + response.statusCode() + " during pairing exchange.");
            }

        } catch (Exception e) {
            return new PairingResult(false, null, "Could not connect to ContextClip backend: " + e.getMessage());
        }

        return new PairingResult(false, null, "Pairing failed unexpectedly.");
    }

    private static String deriveExchangeUrl(String apiEndpointUrl) {
        if (apiEndpointUrl == null || apiEndpointUrl.isBlank()) {
            return "http://localhost:8080/api/agent/pairing/exchange";
        }
        // If apiEndpointUrl ends with /api/clipboard, replace with /api/agent/pairing/exchange
        String trimmed = apiEndpointUrl.trim();
        if (trimmed.contains("/api/")) {
            String base = trimmed.substring(0, trimmed.indexOf("/api/"));
            return base + "/api/agent/pairing/exchange";
        }
        return "http://localhost:8080/api/agent/pairing/exchange";
    }
}