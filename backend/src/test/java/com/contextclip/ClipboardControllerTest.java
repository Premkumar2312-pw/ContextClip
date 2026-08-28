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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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
    void testPostValidClipboardContentReturns201AndSequentialId() throws Exception {
        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"docker compose up --build\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"SELECT * FROM employees;\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void testGetClipboardReturnsStoredEntries() throws Exception {
        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"First item\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Second item\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/clipboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].content").value("First item"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].content").value("Second item"));
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
