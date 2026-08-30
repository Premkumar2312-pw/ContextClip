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

import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class ClipboardSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClipboardService clipboardService;

    @BeforeEach
    void setUp() {
        clipboardService.clear();
        clipboardService.save("docker compose up --build");
        clipboardService.save("git push origin main");
        clipboardService.save("SELECT * FROM employees;");
        clipboardService.save("public class Hello {\n    public static void main(String[] args) {}\n}");
        clipboardService.save("mvn spring-boot:run");
    }

    @Test
    void testSearchWithoutParametersReturnsAll() throws Exception {
        mockMvc.perform(get("/api/clipboard/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void testSearchWithBlankParametersReturnsAll() throws Exception {
        mockMvc.perform(get("/api/clipboard/search?q=&type=&technology=&category="))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void testSearchFreeTextCaseInsensitive() throws Exception {
        mockMvc.perform(get("/api/clipboard/search?q=docker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("docker compose up --build"));

        mockMvc.perform(get("/api/clipboard/search?q=DOCKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("docker compose up --build"));

        mockMvc.perform(get("/api/clipboard/search?q=Docker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("docker compose up --build"));
    }

    @Test
    void testSearchPartialText() throws Exception {
        mockMvc.perform(get("/api/clipboard/search?q=compose"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("docker compose up --build"));
    }

    @Test
    void testSearchByType() throws Exception {
        mockMvc.perform(get("/api/clipboard/search?type=SQL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("SELECT * FROM employees;"));

        mockMvc.perform(get("/api/clipboard/search?type=sql"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("SELECT * FROM employees;"));

        mockMvc.perform(get("/api/clipboard/search?type=TERMINAL_COMMAND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void testSearchByTechnology() throws Exception {
        mockMvc.perform(get("/api/clipboard/search?technology=GIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("git push origin main"));

        mockMvc.perform(get("/api/clipboard/search?technology=java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].technology").value("JAVA"));
    }

    @Test
    void testSearchByCategory() throws Exception {
        mockMvc.perform(get("/api/clipboard/search?category=DEVOPS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mockMvc.perform(get("/api/clipboard/search?category=DATABASE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("SELECT * FROM employees;"));
    }

    @Test
    void testSearchCombinedFilters() throws Exception {
        mockMvc.perform(get("/api/clipboard/search?type=TERMINAL_COMMAND&technology=DOCKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("docker compose up --build"));

        mockMvc.perform(get("/api/clipboard/search?type=TERMINAL_COMMAND&technology=SQL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testSearchCombinedQAndFilters() throws Exception {
        mockMvc.perform(get("/api/clipboard/search?q=spring&technology=MAVEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("mvn spring-boot:run"));

        mockMvc.perform(get("/api/clipboard/search?q=employees&type=SQL&category=DATABASE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("SELECT * FROM employees;"));
    }
}
