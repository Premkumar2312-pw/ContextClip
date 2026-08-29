package com.contextclip;

import com.contextclip.client.AiServiceClient;
import com.contextclip.exception.AiServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiServiceClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testAiServiceUnavailableThrowsAiServiceException() {
        // Pointing to an unreachable port
        AiServiceClient client = new AiServiceClient("http://localhost:59999", 2, objectMapper);

        assertThatThrownBy(() -> client.generateExplanation("Explain something"))
                .isInstanceOf(AiServiceException.class)
                .hasMessageContaining("Failed to communicate with AI Service");
    }
}

