package com.contextclip.agent;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BackendClientTest {

    private HttpServer mockServer;
    private int port;
    private final AtomicReference<String> receivedBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        port = mockServer.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) {
            mockServer.stop(0);
        }
    }

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

        BackendClient client = new BackendClient("http://localhost:" + port + "/api/clipboard", HttpClient.newHttpClient());
        String assignedId = client.sendClipboardContent("SELECT * FROM users;");

        assertEquals("42", assignedId);
        assertEquals("{\"content\":\"SELECT * FROM users;\"}", receivedBody.get());
    }

    @Test
    void testBackendUnavailableReturnsNullWithoutCrashing() {
        // Port with no server running
        BackendClient client = new BackendClient("http://localhost:59999/api/clipboard", HttpClient.newHttpClient());
        String result = client.sendClipboardContent("some text");

        assertNull(result);
    }

    @Test
    void testEmptyOrBlankContentReturnsNull() {
        BackendClient client = new BackendClient("http://localhost:" + port + "/api/clipboard", HttpClient.newHttpClient());
        assertNull(client.sendClipboardContent(""));
        assertNull(client.sendClipboardContent("   "));
        assertNull(client.sendClipboardContent(null));
    }

    @Test
    void testJsonEscaping() {
        String input = "line1\nline2\t\"quoted\"\\backslash";
        String escaped = BackendClient.escapeJson(input);
        assertEquals("line1\\nline2\\t\\\"quoted\\\"\\\\backslash", escaped);
    }
}
