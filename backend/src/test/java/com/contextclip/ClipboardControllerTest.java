package com.contextclip;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contextclip.service.ClipboardService;

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
                .andExpect(jsonPath("$[0].type").value("COMMAND"))
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

    @Test
    void testPostContentExceedingMaxAllowedLengthReturns400() throws Exception {
        String oversized = "x".repeat(ClipboardService.MAX_CLIPBOARD_CONTENT_LENGTH + 1);
        String body = "{\"content\":\"" + oversized + "\"}";

        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Clipboard content exceeds maximum allowed length of " + ClipboardService.MAX_CLIPBOARD_CONTENT_LENGTH + " characters"));
    }

    @Test
    void testExactContentPreservationForComplexContent() throws Exception {
        String complex = "class HelloWorld {\n\tpublic static void main(String[] args) {\n\t\tSystem.out.println(\"Hello! தமிழ் 🙂 🚀 =SUM(A1:A10)\");\n\t}\n}";
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String jsonPayload = mapper.writeValueAsString(java.util.Map.of("content", complex));

        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/clipboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value(complex));
    }

    @Test
    void testDeleteClipboardEntryRemovesEntryImmediately() throws Exception {
        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"temporary snippet\"}"))
                .andExpect(status().isCreated());

        var entries = clipboardService.getAll();
        org.junit.jupiter.api.Assertions.assertFalse(entries.isEmpty());
        Long entryId = entries.get(0).getId();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/clipboard/" + entryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Clipboard entry deleted successfully"));

        mockMvc.perform(get("/api/clipboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testPhase19MetadataSurvivedPostToGet() throws Exception {
        // POST a classified Python entry (simulates what the backend classifier produces)
        String pythonCode = "import pandas as pd\ndf = pd.read_csv(\"employees.csv\")\nprint(df.head())";
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String payload = mapper.writeValueAsString(java.util.Map.of("content", pythonCode));

        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        // GET and verify Phase 19 fields are present in the JSON response
        mockMvc.perform(get("/api/clipboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(notNullValue()))
                .andExpect(jsonPath("$[0].content").value(pythonCode))
                .andExpect(jsonPath("$[0].capturedAt").value(notNullValue()))
                .andExpect(jsonPath("$[0].type").value("CODE"))
                .andExpect(jsonPath("$[0].technology").value("PYTHON"))
                .andExpect(jsonPath("$[0].category").value("PROGRAMMING"))
                // Phase 19 required fields
                .andExpect(jsonPath("$[0].language").value("PYTHON"))
                .andExpect(jsonPath("$[0].technologies").value(org.hamcrest.Matchers.containsString("PYTHON")))
                .andExpect(jsonPath("$[0].categories").value(org.hamcrest.Matchers.containsString("PROGRAMMING")))
                .andExpect(jsonPath("$[0].sensitive").value(false))
                .andExpect(jsonPath("$[0].confidence").value(notNullValue()));
    }

    @Test
    void testPhase19AgentPreClassifiedMetadataSurvivedPostToGet() throws Exception {
        // POST with agent pre-classified metadata (simulates BackendClient payload)
        String body = "{\"content\":\"docker run -it ubuntu\","
                + "\"type\":\"COMMAND\","
                + "\"technology\":\"DOCKER\","
                + "\"category\":\"DEVOPS\","
                + "\"language\":null,"
                + "\"technologies\":\"DOCKER\","
                + "\"categories\":\"DEVOPS\","
                + "\"sensitive\":false,"
                + "\"confidence\":0.97}";

        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/clipboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("COMMAND"))
                .andExpect(jsonPath("$[0].technology").value("DOCKER"))
                .andExpect(jsonPath("$[0].category").value("DEVOPS"))
                .andExpect(jsonPath("$[0].technologies").value("DOCKER"))
                .andExpect(jsonPath("$[0].categories").value("DEVOPS"))
                .andExpect(jsonPath("$[0].sensitive").value(false))
                .andExpect(jsonPath("$[0].confidence").value(0.97));
    }

    @Test
    void testPhase19SensitiveEntryFlaggedCorrectly() throws Exception {
        // This JWT would be marked sensitive by the classifier
        String jwtLike = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String payload = mapper.writeValueAsString(java.util.Map.of("content", jwtLike));

        mockMvc.perform(post("/api/clipboard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/clipboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sensitive").value(true));
    }
}
