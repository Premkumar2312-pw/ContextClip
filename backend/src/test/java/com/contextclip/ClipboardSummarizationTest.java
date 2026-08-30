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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class ClipboardSummarizationTest {

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
        savedEntry = clipboardService.save("git push -u origin main");
    }

    @Test
    void testSummarizeExistingEntrySuccess() throws Exception {
        when(aiServiceClient.generateResponse(anyString()))
                .thenReturn("Pushes local main branch commits to origin remote repository.");

        mockMvc.perform(get("/api/clipboard/" + savedEntry.getId() + "/summarize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedEntry.getId()))
                .andExpect(jsonPath("$.summary").value("Pushes local main branch commits to origin remote repository."));
    }

    @Test
    void testSummarizeNonExistingEntryReturns404() throws Exception {
        mockMvc.perform(get("/api/clipboard/999999/summarize"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testSummarizeInvalidIdReturns400() throws Exception {
        mockMvc.perform(get("/api/clipboard/-1/summarize"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSummarizeAiServiceUnavailableReturns502() throws Exception {
        when(aiServiceClient.generateResponse(anyString()))
                .thenThrow(new AiServiceException("Failed to communicate with AI Service"));

        mockMvc.perform(get("/api/clipboard/" + savedEntry.getId() + "/summarize"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void testSummarizePromptContainsContentAndMetadata() throws Exception {
        when(aiServiceClient.generateResponse(anyString()))
                .thenReturn("Mocked summary");

        mockMvc.perform(get("/api/clipboard/" + savedEntry.getId() + "/summarize"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiServiceClient).generateResponse(promptCaptor.capture());

        String capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt).contains("git push -u origin main");
        assertThat(capturedPrompt).contains("TERMINAL_COMMAND");
        assertThat(capturedPrompt).contains("GIT");
        assertThat(capturedPrompt).contains("DEVOPS");
    }

    @Test
    void testBuildSummarizationPromptMethod() {
        String prompt = clipboardService.buildSummarizationPrompt(savedEntry);
        assertThat(prompt).contains("git push -u origin main");
        assertThat(prompt).contains("Detected Type: TERMINAL_COMMAND");
        assertThat(prompt).contains("Detected Technology: GIT");
        assertThat(prompt).contains("Detected Category: DEVOPS");
        assertThat(prompt).contains("Summarize the following clipboard content");
    }

    @Test
    void testExistingExplainEndpointStillWorks() throws Exception {
        when(aiServiceClient.generateResponse(anyString()))
                .thenReturn("Detailed explanation of git push");

        mockMvc.perform(get("/api/clipboard/" + savedEntry.getId() + "/explain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(savedEntry.getId()))
                .andExpect(jsonPath("$.explanation").value("Detailed explanation of git push"));
    }

    @Test
    void testExistingSearchEndpointStillWorks() throws Exception {
        mockMvc.perform(get("/api/clipboard/search?q=origin&technology=GIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("git push -u origin main"))
                .andExpect(jsonPath("$[0].technology").value("GIT"));
    }

    @Test
    void testExistingPostClipboardEndpointStillWorks() throws Exception {
        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"SELECT * FROM users;\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }
}

