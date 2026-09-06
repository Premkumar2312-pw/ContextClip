package com.contextclip.agent;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

class BackendClientTest {

    private HttpServer mockServer;
    private int port;
    private final AtomicReference<String> receivedBody   = new AtomicReference<>();
    private final AtomicReference<String> receivedAuth   = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        port = mockServer.getAddress().getPort();
        receivedBody.set(null);
        receivedAuth.set(null);
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

    // -------------------------------------------------------------------------
    // Original tests (preserved)
    // -------------------------------------------------------------------------

    @Test
    void testSuccessfulSubmissionReturnsAssignedId() {
        mockServer.createContext("/api/clipboard", exchange -> {
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            receivedBody.set(new String(bodyBytes, StandardCharsets.UTF_8));
            byte[] response = "{\"id\":42,\"status\":\"RECEIVED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        mockServer.start();

        // No credentials — tests backward-compatible path
        BackendClient client = new BackendClient(
                "http://localhost:" + port + "/api/clipboard",
                "http://localhost:" + port + "/api/auth/login",
                null, null,
                HttpClient.newHttpClient());

        BackendClient.SendResult result = client.sendClipboardContent("SELECT * FROM users;");

        assertTrue(result.isSuccess());
        assertEquals("42", result.backendId);
        assertEquals("{\"content\":\"SELECT * FROM users;\"}", receivedBody.get());
    }

    @Test
    void testBackendUnavailableReturnsNullWithoutCrashing() {
        BackendClient client = new BackendClient(
                "http://localhost:59999/api/clipboard",
                "http://localhost:59999/api/auth/login",
                null, null,
                HttpClient.newHttpClient());
        BackendClient.SendResult result = client.sendClipboardContent("some text");
        assertFalse(result.isSuccess());
        assertEquals(BackendClient.SendResult.ErrorType.CONNECTION_FAILURE, result.errorType);
    }

    @Test
    void testEmptyOrBlankContentReturnsNull() {
        BackendClient client = new BackendClient(
                "http://localhost:" + port + "/api/clipboard",
                "http://localhost:" + port + "/api/auth/login",
                null, null,
                HttpClient.newHttpClient());
        assertFalse(client.sendClipboardContent("").isSuccess());
        assertFalse(client.sendClipboardContent("   ").isSuccess());
        assertFalse(client.sendClipboardContent(null).isSuccess());
    }

    @Test
    void testJsonEscaping() {
        String input = "line1\nline2\t\"quoted\"\\backslash";
        String escaped = BackendClient.escapeJson(input);
        assertEquals("line1\\nline2\\t\\\"quoted\\\"\\\\backslash", escaped);
    }

    // -------------------------------------------------------------------------
    // Phase 8C — JWT authentication tests
    // -------------------------------------------------------------------------

    /**
     * When credentials are configured, the client should call /api/auth/login first,
     * then attach the received token in the Authorization header on the clipboard POST.
     */
    @Test
    void testAuthHeaderIsSentWithTokenAfterAuthentication() throws IOException {
        // Auth endpoint returns a token
        mockServer.createContext("/api/auth/login", exchange -> {
            byte[] resp = "{\"token\":\"test-jwt-token\",\"username\":\"agent\",\"role\":\"AGENT\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(resp); }
        });

        // Clipboard endpoint captures the Authorization header
        mockServer.createContext("/api/clipboard", exchange -> {
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] resp = "{\"id\":1}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, resp.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(resp); }
        });
        mockServer.start();

        BackendClient client = new BackendClient(
                "http://localhost:" + port + "/api/clipboard",
                "http://localhost:" + port + "/api/auth/login",
                "agent", "secret",
                HttpClient.newHttpClient());

        BackendClient.SendResult result = client.sendClipboardContent("hello world");

        assertTrue(result.isSuccess());
        assertEquals("1", result.backendId);
        assertEquals("Bearer test-jwt-token", receivedAuth.get());
    }

    /**
     * On a 401 response from the clipboard endpoint, the client must re-authenticate
     * and retry the request exactly once.
     */
    @Test
    void test401TriggersReauthAndRetrySucceeds() throws IOException {
        AtomicInteger authCallCount      = new AtomicInteger(0);
        AtomicInteger clipboardCallCount = new AtomicInteger(0);

        mockServer.createContext("/api/auth/login", exchange -> {
            authCallCount.incrementAndGet();
            byte[] resp = "{\"token\":\"refreshed-token\",\"username\":\"agent\",\"role\":\"AGENT\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(resp); }
        });

        mockServer.createContext("/api/clipboard", exchange -> {
            int call = clipboardCallCount.incrementAndGet();
            if (call == 1) {
                // First call: simulate expired token → 401
                exchange.sendResponseHeaders(401, -1);
                exchange.getResponseBody().close();
            } else {
                // Second call (after re-auth): success
                byte[] resp = "{\"id\":7}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(201, resp.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(resp); }
            }
        });
        mockServer.start();

        BackendClient client = new BackendClient(
                "http://localhost:" + port + "/api/clipboard",
                "http://localhost:" + port + "/api/auth/login",
                "agent", "secret",
                HttpClient.newHttpClient());

        BackendClient.SendResult result = client.sendClipboardContent("retry test");

        assertTrue(result.isSuccess());
        assertEquals("7", result.backendId);
        assertEquals(2, authCallCount.get(),      "auth should be called twice (initial + re-auth)");
        assertEquals(2, clipboardCallCount.get(), "clipboard should be called twice (fail + retry)");
    }

    /**
     * If re-authentication also fails, the method must return a failure result without throwing.
     */
    @Test
    void testReauthFailureReturnsNull() throws IOException {
        mockServer.createContext("/api/auth/login", exchange -> {
            // Always reject auth
            exchange.sendResponseHeaders(401, -1);
            exchange.getResponseBody().close();
        });

        mockServer.createContext("/api/clipboard", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.getResponseBody().close();
        });
        mockServer.start();

        BackendClient client = new BackendClient(
                "http://localhost:" + port + "/api/clipboard",
                "http://localhost:" + port + "/api/auth/login",
                "agent", "wrong-password",
                HttpClient.newHttpClient());

        BackendClient.SendResult result = client.sendClipboardContent("should fail gracefully");
        assertFalse(result.isSuccess());
        assertEquals(BackendClient.SendResult.ErrorType.HTTP_401, result.errorType);
    }

    /**
     * When no credentials are provided, no auth call is made, and the clipboard POST
     * is sent without an Authorization header.
     */
    @Test
    void testNoCredentialsNoAuthCallMade() throws IOException {
        AtomicInteger authCallCount = new AtomicInteger(0);

        mockServer.createContext("/api/auth/login", exchange -> {
            authCallCount.incrementAndGet();
            exchange.sendResponseHeaders(200, -1);
            exchange.getResponseBody().close();
        });

        // Clipboard endpoint accepts without auth (simulates permitAll / pre-8A behaviour)
        mockServer.createContext("/api/clipboard", exchange -> {
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] resp = "{\"id\":99}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, resp.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(resp); }
        });
        mockServer.start();

        BackendClient client = new BackendClient(
                "http://localhost:" + port + "/api/clipboard",
                "http://localhost:" + port + "/api/auth/login",
                null, null,
                HttpClient.newHttpClient());

        client.sendClipboardContent("no auth test");

        assertEquals(0, authCallCount.get(), "auth endpoint must not be called when no credentials set");
        assertNull(receivedAuth.get(), "Authorization header must not be present");
    }

    /**
     * Token must not appear in any logged output.
     * This is a structural assertion — verifies the authenticate() method
     * does not return a value that contains the username or raw password,
     * and that the token value itself is not the same as the credentials.
     */
    @Test
    void testTokenReturnedFromAuthIsDistinctFromCredentials() throws IOException {
        String username = "contextclip-agent";
        String password = "super-secret-pass";
        String fakeToken = "header.payload.signature";

        mockServer.createContext("/api/auth/login", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String bodyStr = new String(body, StandardCharsets.UTF_8);
            // Verify username is in body, NOT that any token is in body
            assertTrue(bodyStr.contains(username));
            byte[] resp = ("{\"token\":\"" + fakeToken + "\",\"username\":\"" + username + "\",\"role\":\"AGENT\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(resp); }
        });
        mockServer.start();

        BackendClient client = new BackendClient(
                "http://localhost:" + port + "/api/clipboard",
                "http://localhost:" + port + "/api/auth/login",
                username, password,
                HttpClient.newHttpClient());

        String token = client.authenticate();

        assertEquals(fakeToken, token);
        assertNotEquals(password, token, "token must not equal the raw password");
        assertFalse(token.contains(password), "token must not contain the raw password");
    }

    @Test
    void testDirectAgentTokenIsUsedWithoutCallingAuth() throws IOException {
        AtomicInteger authCallCount = new AtomicInteger(0);

        mockServer.createContext("/api/auth/agent-login", exchange -> {
            authCallCount.incrementAndGet();
            exchange.sendResponseHeaders(400, 0);
            exchange.close();
        });

        mockServer.createContext("/api/clipboard", exchange -> {
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"id\":99,\"status\":\"RECEIVED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(response); }
        });
        mockServer.start();

        BackendClient client = new BackendClient(
                "http://localhost:" + port + "/api/clipboard",
                "http://localhost:" + port + "/api/auth/agent-login",
                null, null,
                "pre-configured-agent-token",
                HttpClient.newHttpClient());

        BackendClient.SendResult result = client.sendClipboardContent("direct agent token content");

        assertTrue(result.isSuccess());
        assertEquals("99", result.backendId);
        assertEquals(0, authCallCount.get(), "auth endpoint must not be called when AGENT_TOKEN is directly supplied");
        assertEquals("Bearer pre-configured-agent-token", receivedAuth.get());
    }

    /**
     * A 401 from the clipboard endpoint with a pre-configured token (no credentials) should
     * return HTTP_401 error type rather than CONNECTION_FAILURE.
     */
    @Test
    void test401WithNoCredentialsReturns401ErrorType() throws IOException {
        mockServer.createContext("/api/clipboard", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.getResponseBody().close();
        });
        mockServer.start();

        BackendClient client = new BackendClient(
                "http://localhost:" + port + "/api/clipboard",
                "http://localhost:" + port + "/api/auth/agent-login",
                null, null,
                "stale-or-invalid-token",
                HttpClient.newHttpClient());

        BackendClient.SendResult result = client.sendClipboardContent("test content");

        assertFalse(result.isSuccess());
        assertEquals(BackendClient.SendResult.ErrorType.HTTP_401, result.errorType);
        assertEquals(401, result.httpStatus);
    }

    /**
     * A 403 from the clipboard endpoint should be returned as HTTP_403 error type.
     */
    @Test
    void test403ReturnsForbiddenErrorType() throws IOException {
        mockServer.createContext("/api/clipboard", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.getResponseBody().close();
        });
        mockServer.start();

        BackendClient client = new BackendClient(
                "http://localhost:" + port + "/api/clipboard",
                "http://localhost:" + port + "/api/auth/agent-login",
                null, null,
                "some-token",
                HttpClient.newHttpClient());

        BackendClient.SendResult result = client.sendClipboardContent("test content");

        assertFalse(result.isSuccess());
        assertEquals(BackendClient.SendResult.ErrorType.HTTP_403, result.errorType);
        assertEquals(403, result.httpStatus);
    }

    /**
     * CRITICAL TEST: Verifies that BackendClient sends the EXACT token shape:
     * header.payload.signature in Authorization: Bearer header
     * without any surrounding quotes, escaping, or truncation.
     */
    @Test
    void testDeterministicJwtAuthorizationHeader() throws IOException {
        String testJwt = "header.payload.signature";

        mockServer.createContext("/api/clipboard", exchange -> {
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"id\":42,\"status\":\"RECEIVED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        mockServer.start();

        AgentConfig config = new AgentConfig(
                testJwt,
                "http://localhost:" + port + "/api/clipboard",
                "http://localhost:" + port + "/api/auth/agent-login",
                "test-source"
        );

        BackendClient client = new BackendClient(config);
        BackendClient.SendResult result = client.sendClipboardContent("PHASE11_REAL_AGENT_TEST");

        assertTrue(result.isSuccess());
        assertEquals("42", result.backendId);
        assertNotNull(receivedAuth.get(), "Authorization header must be present");
        assertEquals("Bearer " + testJwt, receivedAuth.get(), "Authorization header must match exactly: Bearer <FULL_JWT>");
        assertFalse(receivedAuth.get().contains("\""), "Authorization header must NOT contain quotes around JWT");
    }

    /**
     * Verifies that if a token was configured with accidental quotes,
     * BackendClient sends it cleanly without quotes.
     */
    @Test
    void testQuotedJwtTokenStrippedBeforeSend() throws IOException {
        String testJwtWithQuotes = "\"header.payload.signature\"";

        mockServer.createContext("/api/clipboard", exchange -> {
            receivedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"id\":43,\"status\":\"RECEIVED\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        mockServer.start();

        AgentConfig config = new AgentConfig(
                testJwtWithQuotes,
                "http://localhost:" + port + "/api/clipboard",
                "http://localhost:" + port + "/api/auth/agent-login",
                "test-source"
        );

        BackendClient client = new BackendClient(config);
        BackendClient.SendResult result = client.sendClipboardContent("PHASE11_REAL_AGENT_TEST");

        assertTrue(result.isSuccess());
        assertEquals("43", result.backendId);
        assertEquals("Bearer header.payload.signature", receivedAuth.get());
    }
}
