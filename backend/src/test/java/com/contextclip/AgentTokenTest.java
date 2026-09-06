package com.contextclip;

import com.contextclip.model.ClipboardEntry;
import com.contextclip.model.User;
import com.contextclip.repository.ClipboardRepository;
import com.contextclip.repository.UserRepository;
import com.contextclip.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 11 — Tests for POST /api/auth/agent-token personal agent JWT endpoint
 * and AGENT role access control enforcement.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AgentTokenTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClipboardRepository clipboardRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_USERNAME = "agenttest";
    private static final String TEST_PASSWORD = "AgentPassword123!";

    @BeforeEach
    void setUp() {
        clipboardRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new User(TEST_USERNAME, passwordEncoder.encode(TEST_PASSWORD), "USER"));
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    /** Obtain a USER JWT via /api/auth/login for the test user. */
    private String loginAndGetUserToken() throws Exception {
        String body = """
            {"username":"%s","password":"%s"}
            """.formatted(TEST_USERNAME, TEST_PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    /** Exchange a USER token for an AGENT token via /api/auth/agent-token. */
    private String getAgentToken(String userToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/agent-token")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    // ─── Test 1: agent-token endpoint produces a 3-part JWT ──────────────────

    @Test
    void agentTokenEndpointProducesThreePartJwt() throws Exception {
        String userToken  = loginAndGetUserToken();
        String agentToken = getAgentToken(userToken);

        assertNotNull(agentToken, "agent token must not be null");
        assertFalse(agentToken.isBlank(), "agent token must not be blank");
        long dots = agentToken.chars().filter(c -> c == '.').count();
        assertEquals(2, dots, "JWT must have exactly 2 dots (3 parts): header.payload.signature");
    }

    // ─── Test 2: generated JWT payload contains role=AGENT ───────────────────

    @Test
    void agentTokenPayloadContainsRoleAgent() throws Exception {
        String userToken  = loginAndGetUserToken();
        String agentToken = getAgentToken(userToken);

        String role = jwtService.extractRole(agentToken);
        assertEquals("AGENT", role, "JWT claim 'role' must equal AGENT");
    }

    // ─── Test 3: generated JWT subject matches authenticated username ─────────

    @Test
    void agentTokenSubjectMatchesAuthenticatedUsername() throws Exception {
        String userToken  = loginAndGetUserToken();
        String agentToken = getAgentToken(userToken);

        String subject = jwtService.extractUsername(agentToken);
        assertEquals(TEST_USERNAME, subject, "JWT 'sub' must equal the authenticated user's username");
    }

    // ─── Test 4: AGENT token can POST /api/clipboard ─────────────────────────

    @Test
    void agentTokenCanPostToClipboard() throws Exception {
        String userToken  = loginAndGetUserToken();
        String agentToken = getAgentToken(userToken);

        mockMvc.perform(post("/api/clipboard")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"kubectl get pods\"}"))
                .andExpect(status().isCreated());
    }

    // ─── Test 5: AGENT token cannot GET /api/clipboard ───────────────────────

    @Test
    void agentTokenCannotGetClipboard() throws Exception {
        String userToken  = loginAndGetUserToken();
        String agentToken = getAgentToken(userToken);

        mockMvc.perform(get("/api/clipboard")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    // ─── Test 6: AGENT token cannot DELETE clipboard entries ─────────────────

    @Test
    void agentTokenCannotDeleteClipboard() throws Exception {
        String userToken  = loginAndGetUserToken();
        String agentToken = getAgentToken(userToken);

        mockMvc.perform(delete("/api/clipboard/1")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    // ─── Test 7: AGENT token cannot search clipboard ─────────────────────────

    @Test
    void agentTokenCannotSearchClipboard() throws Exception {
        String userToken  = loginAndGetUserToken();
        String agentToken = getAgentToken(userToken);

        mockMvc.perform(get("/api/clipboard/search")
                        .param("q", "docker")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    // ─── Test 8: AGENT token cannot access analytics ─────────────────────────

    @Test
    void agentTokenCannotAccessAnalytics() throws Exception {
        String userToken  = loginAndGetUserToken();
        String agentToken = getAgentToken(userToken);

        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    // ─── Test 9: AGENT token cannot POST /api/clipboard/ask ──────────────────

    @Test
    void agentTokenCannotPostAsk() throws Exception {
        String userToken  = loginAndGetUserToken();
        String agentToken = getAgentToken(userToken);

        mockMvc.perform(post("/api/clipboard/ask")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What did I copy?\"}"))
                .andExpect(status().isForbidden());
    }

    // ─── Test 10: clipboard entry created by AGENT belongs to the user ────────

    @Test
    void clipboardCreatedByAgentBelongsToAuthenticatedUser() throws Exception {
        String userToken  = loginAndGetUserToken();
        String agentToken = getAgentToken(userToken);

        // Post a clipboard entry using the AGENT token
        mockMvc.perform(post("/api/clipboard")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"agent captured this\"}"))
                .andExpect(status().isCreated());

        // Verify the entry was saved under the correct user in the database
        User user = userRepository.findByUsername(TEST_USERNAME).orElseThrow();
        List<ClipboardEntry> entries = clipboardRepository.findAll();
        assertFalse(entries.isEmpty(), "At least one clipboard entry must exist");
        assertTrue(entries.stream().allMatch(e -> e.getUser() != null && e.getUser().getId().equals(user.getId())),
                "All clipboard entries created via agent token must belong to user: " + TEST_USERNAME);
    }

    // ─── Bonus: agent-token endpoint requires auth (USER JWT) ────────────────

    @Test
    void agentTokenEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/agent-token"))
                .andExpect(status().isUnauthorized());
    }

    // ─── Bonus: AGENT token cannot call agent-token endpoint ─────────────────

    @Test
    void agentTokenCannotGenerateAnotherAgentToken() throws Exception {
        String userToken  = loginAndGetUserToken();
        String agentToken = getAgentToken(userToken);

        // AGENT does not have ROLE_USER, so /api/auth/agent-token (hasRole("USER")) must forbid it
        mockMvc.perform(post("/api/auth/agent-token")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }
}
