package com.contextclip;

import com.contextclip.client.AiServiceClient;
import com.contextclip.model.ClipboardEntry;
import com.contextclip.service.ClipboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClipboardService clipboardService;

    @MockitoBean
    private AiServiceClient aiServiceClient;

    @BeforeEach
    void setUp() {
        clipboardService.clear();
    }

    @Test
    void testEmptyDatabaseAnalytics() throws Exception {
        mockMvc.perform(get("/api/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEntries").value(0))
                .andExpect(jsonPath("$.mostUsedType").value(nullValue()))
                .andExpect(jsonPath("$.mostUsedTechnology").value(nullValue()))
                .andExpect(jsonPath("$.mostUsedCategory").value(nullValue()));

        mockMvc.perform(get("/api/analytics/by-type"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/analytics/by-technology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/analytics/by-category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/analytics/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testAnalyticsOverviewAndGroupingWithData() throws Exception {
        clipboardService.save("docker compose up --build");
        clipboardService.save("docker ps -a");
        clipboardService.save("docker stop container1");
        clipboardService.save("git push -u origin main");
        clipboardService.save("SELECT * FROM users;");

        // Overview test
        mockMvc.perform(get("/api/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEntries").value(5))
                .andExpect(jsonPath("$.mostUsedType").value("TERMINAL_COMMAND"))
                .andExpect(jsonPath("$.mostUsedTechnology").value("DOCKER"))
                .andExpect(jsonPath("$.mostUsedCategory").value("DEVOPS"));

        // Group by type test with descending order
        mockMvc.perform(get("/api/analytics/by-type"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("TERMINAL_COMMAND"))
                .andExpect(jsonPath("$[0].count").value(4))
                .andExpect(jsonPath("$[1].name").value("SQL"))
                .andExpect(jsonPath("$[1].count").value(1));

        // Group by technology test with descending order
        mockMvc.perform(get("/api/analytics/by-technology"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("DOCKER"))
                .andExpect(jsonPath("$[0].count").value(3))
                .andExpect(jsonPath("$[1].count").value(1));

        // Group by category test with descending order
        mockMvc.perform(get("/api/analytics/by-category"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("DEVOPS"))
                .andExpect(jsonPath("$[0].count").value(4))
                .andExpect(jsonPath("$[1].name").value("DATABASE"))
                .andExpect(jsonPath("$[1].count").value(1));

        // Activity test
        mockMvc.perform(get("/api/analytics/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].count").value(5));
    }

    @Test
    void testExistingFeaturesCompatibility() throws Exception {
        when(aiServiceClient.generateResponse(anyString())).thenReturn("Mocked AI response");

        ClipboardEntry entry = clipboardService.save("SELECT * FROM employees;");

        // 1. POST /api/clipboard
        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"git status\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        // 2. GET /api/clipboard/search
        mockMvc.perform(get("/api/clipboard/search?technology=SQL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("SELECT * FROM employees;"));

        // 3. GET /api/clipboard/{id}/explain
        mockMvc.perform(get("/api/clipboard/" + entry.getId() + "/explain"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation").value("Mocked AI response"));

        // 4. GET /api/clipboard/{id}/summarize
        mockMvc.perform(get("/api/clipboard/" + entry.getId() + "/summarize"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Mocked AI response"));

        // 5. POST /api/clipboard/ask
        mockMvc.perform(post("/api/clipboard/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"What SQL queries do I have?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Mocked AI response"));
    }
}
