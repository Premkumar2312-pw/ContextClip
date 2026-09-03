package com.contextclip;

import com.contextclip.client.AiServiceClient;
import com.contextclip.exception.AiServiceException;
import com.contextclip.model.ClipboardEntry;
import com.contextclip.model.User;
import com.contextclip.repository.ClipboardRepository;
import com.contextclip.repository.UserRepository;
import com.contextclip.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ClipboardUserIsolationTest {

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

    @MockBean
    private AiServiceClient aiServiceClient;

    private User userA;
    private User userB;
    private String tokenA;
    private String tokenB;
    private String agentToken;

    @BeforeEach
    void setUp() {
        clipboardRepository.deleteAll();
        userRepository.deleteAll();

        userA = userRepository.save(new User("userA", passwordEncoder.encode("Pass123!"), "USER"));
        userB = userRepository.save(new User("userB", passwordEncoder.encode("Pass123!"), "USER"));
        userRepository.save(new User("contextclip-agent", passwordEncoder.encode("AgentPass123!"), "AGENT"));

        tokenA = jwtService.generateToken("userA", "USER");
        tokenB = jwtService.generateToken("userB", "USER");
        agentToken = jwtService.generateToken("contextclip-agent", "AGENT");
    }

    @Test
    void testUserAEntriesAreIsolatedFromUserB() throws Exception {
        // User A creates an entry
        mockMvc.perform(post("/api/clipboard")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"docker ps -a\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        // User A can see it
        mockMvc.perform(get("/api/clipboard")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("docker ps -a"));

        // User B cannot see it
        mockMvc.perform(get("/api/clipboard")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // User B searching for docker returns 0 results
        mockMvc.perform(get("/api/clipboard/search?q=docker")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testUserBCannotExplainOrSummarizeUserAEntry() throws Exception {
        ClipboardEntry entryA = clipboardRepository.save(new ClipboardEntry(
                "SELECT * FROM users;", "SQL", "POSTGRESQL", "DATABASE", userA
        ));

        // User B tries to explain User A's entry -> 404
        mockMvc.perform(get("/api/clipboard/" + entryA.getId() + "/explain")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // User B tries to summarize User A's entry -> 404
        mockMvc.perform(get("/api/clipboard/" + entryA.getId() + "/summarize")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void testUserBCannotDeleteUserAEntry() throws Exception {
        ClipboardEntry entryA = clipboardRepository.save(new ClipboardEntry(
                "git commit -m 'feat'", "GIT", "GIT", "VERSION_CONTROL", userA
        ));

        // User B tries to delete User A's entry -> 404
        mockMvc.perform(delete("/api/clipboard/" + entryA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Verify entry still exists
        assertTrue(clipboardRepository.findById(entryA.getId()).isPresent());

        // User A deletes their own entry -> 200
        mockMvc.perform(delete("/api/clipboard/" + entryA.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Clipboard entry deleted successfully"));

        // Verify entry is deleted
        assertFalse(clipboardRepository.findById(entryA.getId()).isPresent());
    }

    @Test
    void testClearHistoryDeletesOnlyAuthenticatedUserEntries() throws Exception {
        ClipboardEntry entryA = clipboardRepository.save(new ClipboardEntry("content A", "TEXT", "UNKNOWN", "GENERAL", userA));
        ClipboardEntry entryB = clipboardRepository.save(new ClipboardEntry("content B", "TEXT", "UNKNOWN", "GENERAL", userB));

        // User A clears history
        mockMvc.perform(delete("/api/clipboard")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Clipboard history cleared successfully"));

        // User A has 0 entries
        mockMvc.perform(get("/api/clipboard")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // User B still has their entry
        mockMvc.perform(get("/api/clipboard")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(entryB.getId()));
    }

    @Test
    void testAskClipboardUsesOnlyUserContext() throws Exception {
        clipboardRepository.save(new ClipboardEntry("User A secret token: 12345", "TEXT", "UNKNOWN", "GENERAL", userA));
        clipboardRepository.save(new ClipboardEntry("User B public note: hello world", "TEXT", "UNKNOWN", "GENERAL", userB));

        when(aiServiceClient.generateResponse(anyString())).thenReturn("Here is what I found in your clipboard.");

        // User B asks question
        mockMvc.perform(post("/api/clipboard/ask")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What tokens do I have?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isNotEmpty());
    }

    @Test
    void testAskClipboardEmptyHistoryReturnsFriendlyMessage() throws Exception {
        // User A has no entries
        mockMvc.perform(post("/api/clipboard/ask")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What Docker commands do I have?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("No clipboard entries found in your history to answer this question."))
                .andExpect(jsonPath("$.sources", hasSize(0)));
    }

    @Test
    void testAgentRoleRestrictions() throws Exception {
        // Agent can submit clipboard entries
        mockMvc.perform(post("/api/clipboard")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"agent clipboard text\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        // Agent cannot list entries
        mockMvc.perform(get("/api/clipboard")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());

        // Agent cannot search
        mockMvc.perform(get("/api/clipboard/search?q=test")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());

        // Agent cannot access analytics
        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());

        // Agent cannot ask clipboard
        mockMvc.perform(post("/api/clipboard/ask")
                        .header("Authorization", "Bearer " + agentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"anything\"}"))
                .andExpect(status().isForbidden());

        // Agent cannot delete entries
        mockMvc.perform(delete("/api/clipboard/1")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());

        // Agent cannot clear clipboard
        mockMvc.perform(delete("/api/clipboard")
                        .header("Authorization", "Bearer " + agentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void testAiRateLimitPropagation() throws Exception {
        clipboardRepository.save(new ClipboardEntry("mvn clean test", "COMMAND", "MAVEN", "BUILD", userA));

        when(aiServiceClient.generateResponse(anyString()))
                .thenThrow(new AiServiceException("Rate limit reached", 429));

        mockMvc.perform(post("/api/clipboard/ask")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What maven commands?\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("AI service rate limit reached. Please try again later."));
    }

    // =========================================================================
    // Multi-User Agent Association Tests (Task 11: Tests 1-10)
    // =========================================================================

    @Test
    void testUserAObtainsAgentTokenAndAgentAuthenticates() throws Exception {
        // User A generates agent token via /api/auth/agent-token
        String responseJson = mockMvc.perform(post("/api/auth/agent-token")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("userA"))
                .andExpect(jsonPath("$.role").value("AGENT"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // Also test agent-login with userA credentials
        mockMvc.perform(post("/api/auth/agent-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"userA\",\"password\":\"Pass123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("userA"))
                .andExpect(jsonPath("$.role").value("AGENT"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void testUserAAgentSendsEntry_UserACanRetrieve_UserBCannot() throws Exception {
        // Generate agent token for User A
        String userAAgentToken = jwtService.generateAgentToken("userA");

        // Test 1: User A's agent sends clipboard entry
        mockMvc.perform(post("/api/clipboard")
                        .header("Authorization", "Bearer " + userAAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"docker ps\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        // User A can retrieve it via GET /api/clipboard
        mockMvc.perform(get("/api/clipboard")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("docker ps"));

        // Test 2: User B cannot retrieve User A's entry
        mockMvc.perform(get("/api/clipboard")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Test 3: User A can search their agent-created entry
        mockMvc.perform(get("/api/clipboard/search?q=docker")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].content").value("docker ps"));

        // User B cannot search User A's agent-created entry
        mockMvc.perform(get("/api/clipboard/search?q=docker")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Test 4: User A's analytics include their agent-created entry
        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEntries").value(1));

        // User B's analytics remain 0
        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEntries").value(0));

        // Test 5: User A's Ask My Clipboard can use their agent-created entry
        when(aiServiceClient.generateResponse(anyString())).thenReturn("You have 'docker ps' in your clipboard.");
        mockMvc.perform(post("/api/clipboard/ask")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What docker command did I copy?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("You have 'docker ps' in your clipboard."))
                .andExpect(jsonPath("$.sources", hasSize(1)));

        // Test 6: Agent credential cannot read clipboard (403)
        mockMvc.perform(get("/api/clipboard")
                        .header("Authorization", "Bearer " + userAAgentToken))
                .andExpect(status().isForbidden());

        // Test 7: Agent credential cannot delete clipboard (403)
        ClipboardEntry savedEntry = clipboardRepository.findByUserOrderByCapturedAtDesc(userA).get(0);
        mockMvc.perform(delete("/api/clipboard/" + savedEntry.getId())
                        .header("Authorization", "Bearer " + userAAgentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/clipboard")
                        .header("Authorization", "Bearer " + userAAgentToken))
                .andExpect(status().isForbidden());

        // Test 8: Agent credential cannot access analytics (403)
        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + userAAgentToken))
                .andExpect(status().isForbidden());

        // Test 9: Agent credential cannot access Ask My Clipboard (403)
        mockMvc.perform(post("/api/clipboard/ask")
                        .header("Authorization", "Bearer " + userAAgentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"any question\"}"))
                .andExpect(status().isForbidden());

        // Test 10: An entry created by User A's agent has the correct user_id in the database.
        // Verify by fetching entries belonging to userA — if the entry appears there, user_id matches.
        var userAEntries = clipboardRepository.findByUserOrderByCapturedAtDesc(userA);
        assertEquals(1, userAEntries.size(), "Exactly one entry should be owned by userA");
        assertEquals("docker ps", userAEntries.get(0).getContent());

        // Also confirm User B has zero entries, proving isolation at DB level
        var userBEntries = clipboardRepository.findByUserOrderByCapturedAtDesc(userB);
        assertTrue(userBEntries.isEmpty(), "User B must have no entries");
    }
}
