package com.contextclip.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PairingHandlerTest {

    private File tempConfigFile;
    private HttpServer mockServer;
    private int port;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private final AtomicInteger responseCode = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("");

    @BeforeEach
    void setUp() throws IOException {
        tempConfigFile = File.createTempFile("agent-pairing-test", ".properties");
        AgentConfig.setTestUserConfigFile(tempConfigFile);

        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        port = mockServer.getAddress().getPort();
        receivedBody.set(null);
        responseCode.set(200);
        responseBody.set("");

        mockServer.createContext("/api/agent/pairing/exchange", exchange -> {
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            receivedBody.set(new String(bytes, StandardCharsets.UTF_8));

            byte[] resp = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseCode.get(), resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        mockServer.start();
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
        AgentConfig.setTestUserConfigFile(null);
        if (tempConfigFile != null && tempConfigFile.exists()) {
            tempConfigFile.delete();
        }
    }

    @Test
    void testExtractCode() {
        assertEquals("pair_12345", PairingHandler.extractCode("pair_12345"));
        assertEquals("pair_abc_xyz", PairingHandler.extractCode("contextclip://pair?code=pair_abc_xyz"));
        assertEquals("pair_abc_xyz", PairingHandler.extractCode("contextclip://pair?code=pair_abc_xyz&extra=1"));
        assertNull(PairingHandler.extractCode(null));
        assertNull(PairingHandler.extractCode("   "));
    }

    @Test
    void testInvalidCodePrefixFailsEarly() {
        PairingHandler handler = new PairingHandler();
        PairingHandler.PairingResult result = handler.handlePairing("invalid_code", "http://localhost:" + port + "/api/clipboard");
        assertFalse(result.success());
        assertTrue(result.message().contains("pair_"));
    }

    @Test
    void testSuccessfulPairingFlow() {
        String mockJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJwYWlyaW5ndXNlciIsInJvbGUiOiJBR0VOVCJ9.signature123";
        responseCode.set(200);
        responseBody.set("{\"token\":\"" + mockJwt + "\",\"username\":\"pairinguser\",\"role\":\"AGENT\"}");

        PairingHandler handler = new PairingHandler();
        PairingHandler.PairingResult result = handler.handlePairing("contextclip://pair?code=pair_valid_12345", "http://localhost:" + port + "/api/clipboard");

        assertTrue(result.success());
        assertEquals("pairinguser", result.username());
        assertTrue(result.message().contains("pairinguser"));
        assertNotNull(receivedBody.get());
        assertTrue(receivedBody.get().contains("pair_valid_12345"));

        // Verify token saved to user config
        AgentConfig loaded = AgentConfig.load();
        assertEquals(mockJwt, loaded.getAgentToken());
        assertTrue(loaded.isConfigured());
    }

    @Test
    void testExpiredOrConsumedCodeFails() {
        responseCode.set(401);
        responseBody.set("{\"error\":\"Unauthorized\",\"message\":\"Pairing code has expired\"}");

        PairingHandler handler = new PairingHandler();
        PairingHandler.PairingResult result = handler.handlePairing("pair_expired_12345", "http://localhost:" + port + "/api/clipboard");

        assertFalse(result.success());
        assertTrue(result.message().contains("expired"));
    }

    @Test
    void testMalformedJwtReturnedByBackendFails() {
        responseCode.set(200);
        responseBody.set("{\"token\":\"not-a-valid-jwt\",\"username\":\"baduser\",\"role\":\"AGENT\"}");

        PairingHandler handler = new PairingHandler();
        PairingHandler.PairingResult result = handler.handlePairing("pair_valid_code", "http://localhost:" + port + "/api/clipboard");

        assertFalse(result.success());
        assertTrue(result.message().contains("improperly formatted"));
    }

    @Test
    void testConnectionFailureHandledGracefully() {
        mockServer.stop(0);

        PairingHandler handler = new PairingHandler();
        PairingHandler.PairingResult result = handler.handlePairing("pair_valid_code", "http://localhost:" + port + "/api/clipboard");

        assertFalse(result.success());
        assertTrue(result.message().contains("Could not connect"));
    }
}