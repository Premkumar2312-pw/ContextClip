package com.contextclip.agent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTTP client responsible for sending captured clipboard content to the Spring Boot
 * backend service, including JWT-based authentication.
 *
 * <h2>Authentication flow</h2>
 * <ol>
 *   <li>On the first clipboard POST, the client authenticates against
 *       {@code POST /api/auth/login} using the configured agent credentials.</li>
 *   <li>The received JWT is stored in memory only — it is never written to disk
 *       and is never logged.</li>
 *   <li>Every clipboard POST carries {@code Authorization: Bearer <token>}.</li>
 *   <li>On a 401 response, the client re-authenticates once and retries the
 *       clipboard POST. If re-authentication also fails, the entry is silently
 *       dropped (same behaviour as a network failure).</li>
 * </ol>
 *
 * <h2>No credentials — graceful degradation</h2>
 * <p>If {@code agentUsername} or {@code agentPassword} is null the client skips
 * authentication entirely and attempts the clipboard POST without a token.
 * When the backend is secured the request will receive 401 and the entry will be
 * dropped. This matches the behaviour of the unmodified pre-Phase-8C agent.
 */
public class BackendClient {

    public static final String DEFAULT_ENDPOINT_URL  = "http://localhost:8080/api/clipboard";
    public static final String DEFAULT_AUTH_URL       = "http://localhost:8080/api/auth/login";

    private static final Pattern ID_PATTERN    = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    private final String endpointUrl;
    private final String authUrl;
    private final String agentUsername;
    private final String agentPassword;
    private final HttpClient httpClient;

    /** Cached JWT — held in heap memory only, never persisted. */
    private volatile String cachedToken = null;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Production no-arg constructor. Reads configuration from environment variables:
     * <ul>
     *   <li>{@code DESKTOP_AGENT_API_URL}  — clipboard endpoint (default: localhost:8080)</li>
     *   <li>{@code DESKTOP_AGENT_AUTH_URL} — auth login endpoint (default: localhost:8080)</li>
     *   <li>{@code AGENT_USERNAME}         — agent credential username</li>
     *   <li>{@code AGENT_PASSWORD}         — agent credential password</li>
     * </ul>
     */
    public BackendClient() {
        this(
            envOrDefault("DESKTOP_AGENT_API_URL",  DEFAULT_ENDPOINT_URL),
            envOrDefault("DESKTOP_AGENT_AUTH_URL", DEFAULT_AUTH_URL),
            System.getenv("AGENT_USERNAME"),
            System.getenv("AGENT_PASSWORD"),
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build()
        );
    }

    /**
     * Fully-specified constructor used in tests.
     */
    public BackendClient(String endpointUrl, String authUrl,
                         String agentUsername, String agentPassword,
                         HttpClient httpClient) {
        this.endpointUrl   = endpointUrl;
        this.authUrl       = authUrl;
        this.agentUsername = agentUsername;
        this.agentPassword = agentPassword;
        this.httpClient    = httpClient;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Sends the captured clipboard content to the backend.
     *
     * @param content the raw text from the clipboard
     * @return the assigned backend ID if successful, or {@code null} if the
     *         request could not be completed
     */
    public String sendClipboardContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        // Ensure we have a token before the first attempt
        if (cachedToken == null && hasCredentials()) {
            cachedToken = authenticate();
        }

        String result = postClipboard(content, cachedToken);

        if (result == null && cachedToken != null) {
            // Token may have expired — re-authenticate and retry exactly once
            cachedToken = authenticate();
            if (cachedToken != null) {
                result = postClipboard(content, cachedToken);
            }
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Calls {@code POST /api/auth/login} and returns the JWT token string, or
     * {@code null} if authentication fails for any reason.
     *
     * <p>The token value is never logged.
     */
    String authenticate() {
        if (!hasCredentials()) {
            return null;
        }

        String jsonPayload = "{\"username\":\"" + escapeJson(agentUsername)
                + "\",\"password\":\"" + escapeJson(agentPassword) + "\"}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Matcher matcher = TOKEN_PATTERN.matcher(response.body());
                if (matcher.find()) {
                    // Do NOT log the token value
                    System.out.println("Agent authenticated successfully.");
                    return matcher.group(1);
                }
            }
            System.err.println("Agent authentication failed. Status: " + response.statusCode());
            return null;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            System.err.println("Agent authentication error: " + e.getMessage());
            return null;
        } catch (Exception e) {
            System.err.println("Agent authentication unexpected error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Sends a single clipboard POST. Returns the backend-assigned ID on success
     * ({@code 200} or {@code 201}), or {@code null} on any failure (including 401).
     */
    private String postClipboard(String content, String token) {
        String jsonPayload = "{\"content\":\"" + escapeJson(content) + "\"}";

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json");

            if (token != null && !token.isBlank()) {
                builder.header("Authorization", "Bearer " + token);
            }

            HttpRequest request = builder
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201 || response.statusCode() == 200) {
                Matcher matcher = ID_PATTERN.matcher(response.body());
                if (matcher.find()) {
                    return matcher.group(1);
                }
                return "OK";
            }
            // Return null for 401 and any other non-success status;
            // the caller handles the 401 retry logic
            return null;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasCredentials() {
        return agentUsername != null && !agentUsername.isBlank()
                && agentPassword != null && !agentPassword.isBlank();
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    /**
     * Safely escapes special JSON characters in strings.
     */
    public static String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            switch (ch) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> {
                    if (ch < ' ') {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        return sb.toString();
    }
}
