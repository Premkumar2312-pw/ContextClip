package com.contextclip.agent;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
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
    public static final String DEFAULT_AUTH_URL       = "http://localhost:8080/api/auth/agent-login";

    private static final Pattern ID_PATTERN    = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    private final String endpointUrl;
    private final String authUrl;
    private final String agentUsername;
    private final String agentPassword;
    private final String tokenSource;
    private final HttpClient httpClient;

    /** Cached JWT — held in heap memory only, never persisted. */
    private volatile String cachedToken = null;

    // -------------------------------------------------------------------------
    // Result type for send operations
    // -------------------------------------------------------------------------

    /**
     * Result of a {@link #sendClipboardContent(String)} call.
     * Distinguishes success from specific failure modes.
     */
    public static class SendResult {
        public enum ErrorType { NONE, CONNECTION_FAILURE, HTTP_401, HTTP_403, HTTP_429, HTTP_5XX, HTTP_OTHER }

        public final String backendId;       // non-null on success
        public final ErrorType errorType;
        public final int httpStatus;         // 0 when not an HTTP error

        private SendResult(String backendId, ErrorType errorType, int httpStatus) {
            this.backendId  = backendId;
            this.errorType  = errorType;
            this.httpStatus = httpStatus;
        }

        public boolean isSuccess() { return backendId != null; }

        public static SendResult success(String id)          { return new SendResult(id, ErrorType.NONE, 0); }
        public static SendResult connFailure()               { return new SendResult(null, ErrorType.CONNECTION_FAILURE, 0); }
        public static SendResult httpError(int status) {
            ErrorType t = switch (status) {
                case 401 -> ErrorType.HTTP_401;
                case 403 -> ErrorType.HTTP_403;
                case 429 -> ErrorType.HTTP_429;
                default  -> status >= 500 ? ErrorType.HTTP_5XX : ErrorType.HTTP_OTHER;
            };
            return new SendResult(null, t, status);
        }
    }

    private static final int MAX_OFFLINE_BUFFER = 100;
    private final java.util.Queue<String> offlineBuffer = new java.util.concurrent.ConcurrentLinkedQueue<>();

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Production no-arg constructor. Reads configuration hierarchically via {@link AgentConfig}.
     */
    public BackendClient() {
        this(AgentConfig.load());
    }

    /**
     * Constructor supporting injected {@link AgentConfig}.
     */
    public BackendClient(AgentConfig config) {
        this(
            config.getEndpointUrl(),
            config.getAuthUrl(),
            null, // Do not fall back to shared AGENT_USERNAME
            null, // Do not fall back to shared AGENT_PASSWORD
            config.getAgentToken(),
            config.getTokenSource(),
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build()
        );
    }

    /**
     * Fully-specified constructor used in tests (without pre-set token).
     */
    public BackendClient(String endpointUrl, String authUrl,
                         String agentUsername, String agentPassword,
                         HttpClient httpClient) {
        this(endpointUrl, authUrl, agentUsername, agentPassword, null, null, httpClient);
    }

    /**
     * Fully-specified constructor supporting direct agent token injection.
     */
    public BackendClient(String endpointUrl, String authUrl,
                         String agentUsername, String agentPassword,
                         String agentToken,
                         HttpClient httpClient) {
        this(endpointUrl, authUrl, agentUsername, agentPassword, agentToken, null, httpClient);
    }

    public BackendClient(String endpointUrl, String authUrl,
                         String agentUsername, String agentPassword,
                         String agentToken,
                         String tokenSource,
                         HttpClient httpClient) {
        this.endpointUrl   = endpointUrl;
        this.authUrl       = authUrl;
        this.agentUsername = agentUsername;
        this.agentPassword = agentPassword;
        this.cachedToken   = (agentToken != null && !agentToken.isBlank()) ? AgentConfig.stripQuotes(agentToken.trim()) : null;
        this.tokenSource   = tokenSource != null ? tokenSource : "direct";
        this.httpClient    = httpClient;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Sends the captured clipboard content to the backend.
     *
     * @param content the raw text from the clipboard
     * @return {@link SendResult} describing success or the specific error type
     */
    public SendResult sendClipboardContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return SendResult.connFailure();
        }

        // Ensure we have a token before the first attempt
        if (cachedToken == null && hasCredentials()) {
            cachedToken = authenticate();
        }

        SendResult result = postClipboard(content, cachedToken);

        if (!result.isSuccess() && result.errorType == SendResult.ErrorType.HTTP_401 && hasCredentials()) {
            // Token may have expired — re-authenticate and retry exactly once
            cachedToken = authenticate();
            if (cachedToken != null) {
                result = postClipboard(content, cachedToken);
            }
        }

        if (result.isSuccess()) {
            flushOfflineBuffer();
        } else if (result.errorType == SendResult.ErrorType.CONNECTION_FAILURE) {
            bufferOfflineContent(content);
        }

        return result;
    }

    private void bufferOfflineContent(String content) {
        if (offlineBuffer.size() >= MAX_OFFLINE_BUFFER) {
            offlineBuffer.poll();
        }
        offlineBuffer.offer(content);
    }

    private void flushOfflineBuffer() {
        while (!offlineBuffer.isEmpty()) {
            String buffered = offlineBuffer.peek();
            if (buffered == null) break;
            SendResult res = postClipboard(buffered, cachedToken);
            if (res.isSuccess()) {
                offlineBuffer.poll();
                System.out.println("Flushed offline clipboard entry (Backend ID: " + res.backendId + ").");
            } else {
                break;
            }
        }
    }

    public int getOfflineBufferSize() {
        return offlineBuffer.size();
    }

    public void clearOfflineBuffer() {
        offlineBuffer.clear();
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
     * Sends a single clipboard POST. Returns a {@link SendResult} indicating
     * success (with backend-assigned ID) or the specific failure type.
     */
    private SendResult postClipboard(String content, String token) {
        String jsonPayload = "{\"content\":\"" + escapeJson(content) + "\"}";

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json");

            if (token != null && !token.isBlank()) {
                String cleanToken = AgentConfig.stripQuotes(token.trim());
                builder.header("Authorization", "Bearer " + cleanToken);

                // Safe diagnostic logging — NEVER prints the actual JWT
                AgentConfig.JwtClaimsSummary summary = AgentConfig.inspectTokenMetadata(cleanToken);
                System.out.println("[Agent] Sending POST " + endpointUrl
                        + " (token source: " + (tokenSource != null ? tokenSource : "in-memory")
                        + ", token length: " + cleanToken.length()
                        + ", role: " + (summary.role != null ? summary.role : "unknown")
                        + ", parts: " + summary.partsCount + ")");
            } else {
                System.out.println("[Agent] Sending POST " + endpointUrl + " (no token)");
            }

            HttpRequest request = builder
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201 || response.statusCode() == 200) {
                Matcher matcher = ID_PATTERN.matcher(response.body());
                if (matcher.find()) {
                    return SendResult.success(matcher.group(1));
                }
                return SendResult.success("OK");
            }

            // Return specific HTTP error result — allows caller to distinguish 401 from 5xx
            return SendResult.httpError(response.statusCode());

        } catch (ConnectException | HttpTimeoutException e) {
            return SendResult.connFailure();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return SendResult.connFailure();
        } catch (Exception e) {
            return SendResult.connFailure();
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

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return null;
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
