package com.contextclip;

import com.contextclip.service.ClipboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class ClipboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClipboardService clipboardService;

    @BeforeEach
    void setUp() {
        clipboardService.clear();
    }

    @Test
    void testPostValidClipboardContentReturns201AndGeneratedId() throws Exception {
        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"docker compose up --build\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"SELECT * FROM employees;\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void testGetClipboardReturnsPersistedEntriesWithClassification() throws Exception {
        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"docker compose up --build\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"SELECT * FROM employees;\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/clipboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(notNullValue()))
                .andExpect(jsonPath("$[0].content").value("docker compose up --build"))
                .andExpect(jsonPath("$[0].capturedAt").value(notNullValue()))
                .andExpect(jsonPath("$[0].type").value("TERMINAL_COMMAND"))
                .andExpect(jsonPath("$[0].technology").value("DOCKER"))
                .andExpect(jsonPath("$[0].category").value("DEVOPS"))
                .andExpect(jsonPath("$[1].id").value(notNullValue()))
                .andExpect(jsonPath("$[1].content").value("SELECT * FROM employees;"))
                .andExpect(jsonPath("$[1].capturedAt").value(notNullValue()))
                .andExpect(jsonPath("$[1].type").value("SQL"))
                .andExpect(jsonPath("$[1].technology").value("SQL"))
                .andExpect(jsonPath("$[1].category").value("DATABASE"));
    }

    @Test
    void testPostBlankOrEmptyContentReturns400() throws Exception {
        // Empty content string
        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\"}"))
                .andExpect(status().isBadRequest());

        // Whitespace only
        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());

        // Null content
        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":null}"))
                .andExpect(status().isBadRequest());

        // Empty body
        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
