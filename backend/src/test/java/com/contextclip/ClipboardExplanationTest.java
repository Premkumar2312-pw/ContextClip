package com.contextclip;

import com.contextclip.client.AiServiceClient;
import com.contextclip.exception.AiServiceException;
import com.contextclip.model.ClipboardEntry;
import com.contextclip.service.ClipboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClipboardExplanationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClipboardService clipboardService;

    @MockitoBean
    private AiServiceClient aiServiceClient;

    private ClipboardEntry savedEntry;

    @BeforeEach
    void setUp() {
        clipboardService.clear();
        savedEntry = clipboardService.save("docker compose up --build");
    }

    @Test
    void testExplainExistingEntrySuccess() throws Exception {
        when(aiServiceClient.generateResponse(anyString()))
                .thenReturn("This command starts the Docker Compose services and rebuilds images.");

        mockMvc.perform(get("/api/clipboard/" + savedEntry.getId() + "/explain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedEntry.getId()))
                .andExpect(jsonPath("$.explanation").value("This command starts the Docker Compose services and rebuilds images."));
    }

    @Test
    void testExplainNonExistingEntryReturns404() throws Exception {
        mockMvc.perform(get("/api/clipboard/999999/explain"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testExplainAiServiceUnavailableReturns502() throws Exception {
        when(aiServiceClient.generateResponse(anyString()))
                .thenThrow(new AiServiceException("Failed to communicate with AI Service"));

        mockMvc.perform(get("/api/clipboard/" + savedEntry.getId() + "/explain"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void testExplainPromptContainsContentAndMetadata() throws Exception {
        when(aiServiceClient.generateResponse(anyString()))
                .thenReturn("Mocked explanation");

        mockMvc.perform(get("/api/clipboard/" + savedEntry.getId() + "/explain"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiServiceClient).generateResponse(promptCaptor.capture());

        String capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt).contains("docker compose up --build");
        assertThat(capturedPrompt).contains("TERMINAL_COMMAND");
        assertThat(capturedPrompt).contains("DOCKER");
        assertThat(capturedPrompt).contains("DEVOPS");
    }

    @Test
    void testBuildExplanationPromptMethod() {
        String prompt = clipboardService.buildExplanationPrompt(savedEntry);
        assertThat(prompt).contains("docker compose up --build");
        assertThat(prompt).contains("Detected Type: TERMINAL_COMMAND");
        assertThat(prompt).contains("Detected Technology: DOCKER");
        assertThat(prompt).contains("Detected Category: DEVOPS");
    }
}
