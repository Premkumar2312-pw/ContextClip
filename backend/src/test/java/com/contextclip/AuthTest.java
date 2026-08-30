package com.contextclip;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.contextclip.model.User;
import com.contextclip.repository.ClipboardRepository;
import com.contextclip.repository.UserRepository;
import com.contextclip.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthTest {

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

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        clipboardRepository.deleteAll();
    }

    @Test
    void testRegisterValidUser() throws Exception {
        String requestJson = """
            {
                "username": "prem",
                "password": "StrongPassword123!"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User registered successfully"));

        assertTrue(userRepository.existsByUsername("prem"));
    }

    @Test
    void testRegisterDuplicateUsernameReturns409() throws Exception {
        userRepository.save(new User("prem", passwordEncoder.encode("Password123!"), "USER"));

        String requestJson = """
            {
                "username": "prem",
                "password": "AnotherPassword123!"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    void testRegisterMissingUsernameReturns400() throws Exception {
        String requestJson = """
            {
                "password": "StrongPassword123!"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void testRegisterBlankUsernameReturns400() throws Exception {
        String requestJson = """
            {
                "username": "   ",
                "password": "StrongPassword123!"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void testRegisterMissingPasswordReturns400() throws Exception {
        String requestJson = """
            {
                "username": "prem"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void testRegisterShortPasswordReturns400() throws Exception {
        String requestJson = """
            {
                "username": "prem",
                "password": "123"
            }
            """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void testPasswordIsStoredHashedNotPlaintext() throws Exception {
        String rawPassword = "MySecretPassword123!";
        String requestJson = String.format("""
            {
                "username": "secureuser",
                "password": "%s"
            }
            """, rawPassword);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated());

        User user = userRepository.findByUsername("secureuser").orElseThrow();
        assertNotNull(user.getPassword());
        assertFalse(user.getPassword().contains(rawPassword));
        assertTrue(passwordEncoder.matches(rawPassword, user.getPassword()));
    }

    @Test
    void testLoginValidCredentialsReturns200AndJwt() throws Exception {
        userRepository.save(new User("prem", passwordEncoder.encode("StrongPassword123!"), "USER"));

        String requestJson = """
            {
                "username": "prem",
                "password": "StrongPassword123!"
            }
            """;

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.username").value("prem"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        JsonNode jsonNode = objectMapper.readTree(responseContent);
        String token = jsonNode.get("token").asText();

        assertNotNull(token);
        assertTrue(jwtService.validateToken(token));
        org.junit.jupiter.api.Assertions.assertEquals("prem", jwtService.extractUsername(token));
        org.junit.jupiter.api.Assertions.assertEquals("USER", jwtService.extractRole(token));
    }

    @Test
    void testLoginInvalidCredentialsReturns401() throws Exception {
        userRepository.save(new User("prem", passwordEncoder.encode("StrongPassword123!"), "USER"));

        String requestJson = """
            {
                "username": "prem",
                "password": "WrongPassword!"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void testLoginNonExistentUserReturns401() throws Exception {
        String requestJson = """
            {
                "username": "nonexistent",
                "password": "SomePassword123!"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void testPublicHealthEndpointAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void testProtectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/clipboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testProtectedEndpointWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/clipboard")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testProtectedEndpointWithExpiredTokenReturns401() throws Exception {
        JwtService shortLivedJwtService = new JwtService(
                "contextclip-default-secret-key-at-least-256-bits-long-32bytes",
                -10 // expired 10 minutes ago
        );
        String expiredToken = shortLivedJwtService.generateToken("prem", "USER");

        mockMvc.perform(get("/api/clipboard")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testProtectedClipboardEndpointWithValidTokenReturns200() throws Exception {
        userRepository.save(new User("prem", passwordEncoder.encode("Password123!"), "USER"));
        String token = jwtService.generateToken("prem", "USER");

        mockMvc.perform(get("/api/clipboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void testProtectedSearchEndpointWithValidTokenReturns200() throws Exception {
        userRepository.save(new User("prem", passwordEncoder.encode("Password123!"), "USER"));
        String token = jwtService.generateToken("prem", "USER");

        mockMvc.perform(get("/api/clipboard/search")
                        .param("q", "docker")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void testProtectedAnalyticsEndpointWithValidTokenReturns200() throws Exception {
        userRepository.save(new User("prem", passwordEncoder.encode("Password123!"), "USER"));
        String token = jwtService.generateToken("prem", "USER");

        mockMvc.perform(get("/api/analytics/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void testJwtServiceFailsWhenSecretIsMissingOrBlank() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            new JwtService(null, 60);
        });

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            new JwtService("", 60);
        });

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            new JwtService("   ", 60);
        });
    }

    @Test
    void testJwtServiceFailsWhenSecretIsLessThan32Bytes() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> {
            new JwtService("short-secret-less-than-32-bytes", 60);
        });
    }
}
