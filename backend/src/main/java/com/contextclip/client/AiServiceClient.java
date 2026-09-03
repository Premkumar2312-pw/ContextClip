package com.contextclip.client;

import com.contextclip.exception.AiServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
public class AiServiceClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String aiServiceUrl;
    private final int timeoutSeconds;

    public AiServiceClient(
            @Value("${ai.service.url:http://localhost:8000}") String aiServiceUrl,
            @Value("${ai.service.timeout-seconds:60}") int timeoutSeconds,
            ObjectMapper objectMapper) {
        this.aiServiceUrl = aiServiceUrl.replaceAll("/+$", "");
        this.timeoutSeconds = timeoutSeconds;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    public String generateResponse(String prompt) {
        try {
            String requestBody = objectMapper.writeValueAsString(Map.of("prompt", prompt));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiServiceUrl + "/api/ai/generate"))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                throw new AiServiceException("AI Service returned HTTP " + response.statusCode() + ": " + response.body(), response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode responseNode = root.get("response");
            if (responseNode == null || responseNode.isNull() || responseNode.asText().trim().isEmpty()) {
                throw new AiServiceException("AI Service returned an invalid or empty response", 502);
            }

            return responseNode.asText().trim();
        } catch (AiServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new AiServiceException("Failed to communicate with AI Service: " + e.getMessage(), 503, e);
        }
    }

    public String generateExplanation(String prompt) {
        return generateResponse(prompt);
    }
}
