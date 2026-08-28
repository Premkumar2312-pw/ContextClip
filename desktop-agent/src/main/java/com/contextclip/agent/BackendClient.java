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
 * Standard Java HTTP client responsible for sending captured clipboard content
 * to the Spring Boot backend service.
 */
public class BackendClient {

    public static final String DEFAULT_ENDPOINT_URL = "http://localhost:8080/api/clipboard";
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    private final String endpointUrl;
    private final HttpClient httpClient;

    public BackendClient() {
        this(DEFAULT_ENDPOINT_URL, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build());
    }

    public BackendClient(String endpointUrl, HttpClient httpClient) {
        this.endpointUrl = endpointUrl;
        this.httpClient = httpClient;
    }

    /**
     * Sends the captured clipboard content to the backend.
     *
     * @param content the raw text from the clipboard
     * @return the assigned backend ID if successful, or null if unreachable / rejected
     */
    public String sendClipboardContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return null;
        }

        String jsonPayload = "{\"content\":\"" + escapeJson(content) + "\"}";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201 || response.statusCode() == 200) {
                Matcher matcher = ID_PATTERN.matcher(response.body());
                if (matcher.find()) {
                    return matcher.group(1);
                }
                return "OK";
            } else {
                return null;
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
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
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
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
