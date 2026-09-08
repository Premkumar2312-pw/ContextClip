package com.contextclip;

import com.contextclip.model.User;
import com.contextclip.repository.UserRepository;
import com.contextclip.service.AgentPairingService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AgentPairingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AgentPairingService agentPairingService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USER_A = "pairinguser_a";
    private static final String USER_B = "pairinguser_b";
    private static final String PASSWORD = "TestPassword123!";

    @BeforeEach
    void setUp() {
        agentPairingService.clearStore();
        userRepository.deleteAll();
        userRepository.save(new User(USER_A, passwordEncoder.encode(PASSWORD), "USER"));
        userRepository.save(new User(USER_B, passwordEncoder.encode(PASSWORD), "USER"));
    }

    private String loginAndGetToken(String username) throws Exception {
        String body = """
            {"username":"%s","password":"%s"}
            """.formatted(username, PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    @Test
    void unauthenticatedCannotCreatePairingCode() throws Exception {
        mockMvc.perform(post("/api/agent/pairing"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanCreatePairingCode() throws Exception {
        String userToken = loginAndGetToken(USER_A);
        MvcResult result = mockMvc.perform(post("/api/agent/pairing")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(json.has("code"));
        assertTrue(json.has("expiresInSeconds"));
        assertTrue(json.has("pairUrl"));
        String code = json.get("code").asText();
        assertTrue(code.startsWith("pair_"));
        assertEquals("contextclip://pair?code=" + code, json.get("pairUrl").asText());
    }

    @Test
    void exchangePairingCodeProducesUserScopedAgentToken() throws Exception {
        String userTokenA = loginAndGetToken(USER_A);
        MvcResult pairResult = mockMvc.perform(post("/api/agent/pairing")
                        .header("Authorization", "Bearer " + userTokenA))
                .andExpect(status().isOk())
                .andReturn();

        String code = objectMapper.readTree(pairResult.getResponse().getContentAsString()).get("code").asText();

        // Exchange code without auth header
        String exchangeBody = """
            {"code":"%s"}
            """.formatted(code);

        MvcResult exchangeResult = mockMvc.perform(post("/api/agent/pairing/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exchangeBody))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(exchangeResult.getResponse().getContentAsString());
        assertEquals("AGENT", json.get("role").asText());
        assertEquals(USER_A, json.get("username").asText());
        assertNotNull(json.get("token").asText());
        assertEquals(2, json.get("token").asText().chars().filter(c -> c == '.').count());
    }

    @Test
    void pairingCodeCannotBeReused() throws Exception {
        String userTokenA = loginAndGetToken(USER_A);
        MvcResult pairResult = mockMvc.perform(post("/api/agent/pairing")
                        .header("Authorization", "Bearer " + userTokenA))
                .andExpect(status().isOk())
                .andReturn();

        String code = objectMapper.readTree(pairResult.getResponse().getContentAsString()).get("code").asText();
        String exchangeBody = "{\"code\":\"" + code + "\"}";

        // First exchange succeeds
        mockMvc.perform(post("/api/agent/pairing/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exchangeBody))
                .andExpect(status().isOk());

        // Second exchange must fail
        mockMvc.perform(post("/api/agent/pairing/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exchangeBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidOrBogusCodeFails() throws Exception {
        mockMvc.perform(post("/api/agent/pairing/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"pair_bogus_fake_code\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userIsolationPreservedBetweenUsers() throws Exception {
        String tokenA = loginAndGetToken(USER_A);
        String tokenB = loginAndGetToken(USER_B);

        MvcResult resultA = mockMvc.perform(post("/api/agent/pairing")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        String codeA = objectMapper.readTree(resultA.getResponse().getContentAsString()).get("code").asText();

        MvcResult resultB = mockMvc.perform(post("/api/agent/pairing")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andReturn();
        String codeB = objectMapper.readTree(resultB.getResponse().getContentAsString()).get("code").asText();

        assertNotEquals(codeA, codeB);

        // Exchange code B
        MvcResult exchangeB = mockMvc.perform(post("/api/agent/pairing/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + codeB + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(USER_B, objectMapper.readTree(exchangeB.getResponse().getContentAsString()).get("username").asText());
    }
}
