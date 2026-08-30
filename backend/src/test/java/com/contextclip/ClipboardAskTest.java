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

import java.util.List;

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
class ClipboardAskTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClipboardService clipboardService;

    @MockitoBean
    private AiServiceClient aiServiceClient;

    private ClipboardEntry dockerEntry1;
    private ClipboardEntry dockerEntry2;
    private ClipboardEntry gitEntry;
    private ClipboardEntry sqlEntry;

    @BeforeEach
    void setUp() {
        clipboardService.clear();
        dockerEntry1 = clipboardService.save("docker compose up --build");
        dockerEntry2 = clipboardService.save("docker ps -a");
        gitEntry = clipboardService.save("git push -u origin main");
        sqlEntry = clipboardService.save("SELECT * FROM users WHERE active = true;");
    }

    @Test
    void testAskValidQuestionReturnsAnswerAndSources() throws Exception {
        when(aiServiceClient.generateResponse(anyString()))
                .thenReturn("You have copied Docker commands like docker compose up --build and docker ps -a.");

        mockMvc.perform(post("/api/clipboard/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"What Docker commands have I copied?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("You have copied Docker commands like docker compose up --build and docker ps -a."))
                .andExpect(jsonPath("$.sources").isArray())
                .andExpect(jsonPath("$.sources[0]").value(dockerEntry2.getId()))
                .andExpect(jsonPath("$.sources[1]").value(dockerEntry1.getId()));
    }

    @Test
    void testAskMissingQuestionReturns400() throws Exception {
        mockMvc.perform(post("/api/clipboard/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAskBlankQuestionReturns400() throws Exception {
        mockMvc.perform(post("/api/clipboard/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"   \t  \n  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAskExcessivelyLongQuestionReturns400() throws Exception {
        String hugeQuestion = "a".repeat(2001);
        mockMvc.perform(post("/api/clipboard/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"" + hugeQuestion + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testAskPromptContainsQuestionAndContext() throws Exception {
        when(aiServiceClient.generateResponse(anyString()))
                .thenReturn("Mocked answer");

        mockMvc.perform(post("/api/clipboard/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"What SQL queries do I have?\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiServiceClient).generateResponse(promptCaptor.capture());

        String capturedPrompt = promptCaptor.getValue();
        assertThat(capturedPrompt).contains("What SQL queries do I have?");
        assertThat(capturedPrompt).contains("SELECT * FROM users WHERE active = true;");
        assertThat(capturedPrompt).contains("Type: SQL");
        assertThat(capturedPrompt).contains("Technology: SQL");
        assertThat(capturedPrompt).contains("Category: DATABASE");
    }

    @Test
    void testAskLimitsMaxContextEntriesTo10() {
        clipboardService.clear();
        for (int i = 1; i <= 15; i++) {
            clipboardService.save("docker run --name container-" + i + " nginx");
        }

        List<ClipboardEntry> relevant = clipboardService.findRelevantEntries("docker");
        assertThat(relevant).hasSize(10);
    }

    @Test
    void testAskAiServiceUnavailableReturns502() throws Exception {
        when(aiServiceClient.generateResponse(anyString()))
                .thenThrow(new AiServiceException("Failed to communicate with AI Service"));

        mockMvc.perform(post("/api/clipboard/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"What Docker commands have I copied?\"}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void testExistingExplainEndpointStillWorks() throws Exception {
        when(aiServiceClient.generateResponse(anyString()))
                .thenReturn("Detailed explanation of git push");

        mockMvc.perform(get("/api/clipboard/" + gitEntry.getId() + "/explain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(gitEntry.getId()))
                .andExpect(jsonPath("$.explanation").value("Detailed explanation of git push"));
    }

    @Test
    void testExistingSummarizeEndpointStillWorks() throws Exception {
        when(aiServiceClient.generateResponse(anyString()))
                .thenReturn("Summary of SQL query");

        mockMvc.perform(get("/api/clipboard/" + sqlEntry.getId() + "/summarize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sqlEntry.getId()))
                .andExpect(jsonPath("$.summary").value("Summary of SQL query"));
    }

    @Test
    void testExistingSearchEndpointStillWorks() throws Exception {
        mockMvc.perform(get("/api/clipboard/search?technology=DOCKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void testExistingPostClipboardEndpointStillWorks() throws Exception {
        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"public static void main(String[] args) {}\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }
}
