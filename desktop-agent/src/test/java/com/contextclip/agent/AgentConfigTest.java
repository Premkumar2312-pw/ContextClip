package com.contextclip.agent;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class AgentConfigTest {

    @Test
    void testIsJwtStructuredValid() {
        assertTrue(AgentConfig.isJwtStructured("header.payload.signature"));
        assertTrue(AgentConfig.isJwtStructured("eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c2VyIn0.abcdef"));
    }

    @Test
    void testIsJwtStructuredInvalid() {
        assertFalse(AgentConfig.isJwtStructured(null));
        assertFalse(AgentConfig.isJwtStructured(""));
        assertFalse(AgentConfig.isJwtStructured("   "));
        assertFalse(AgentConfig.isJwtStructured("not-a-jwt"));
        assertFalse(AgentConfig.isJwtStructured("only.one-dot"));
        assertFalse(AgentConfig.isJwtStructured("four.dots.in.this.token"));
    }

    @Test
    void testDefaultEndpoints() {
        AgentConfig config = new AgentConfig("a.b.c", null, null, "test");
        assertEquals(AgentConfig.DEFAULT_ENDPOINT_URL, config.getEndpointUrl());
        assertEquals(AgentConfig.DEFAULT_AUTH_URL, config.getAuthUrl());
        assertTrue(config.hasValidToken());
        assertTrue(config.isConfigured());
    }

    @Test
    void testCustomEndpoints() {
        AgentConfig config = new AgentConfig("a.b.c", "http://custom:8080/api/clip", "http://custom:8080/auth", "test");
        assertEquals("http://custom:8080/api/clip", config.getEndpointUrl());
        assertEquals("http://custom:8080/auth", config.getAuthUrl());
        assertEquals("a.b.c", config.getAgentToken());
        assertEquals("test", config.getTokenSource());
    }

    @Test
    void testSaveUserTokenAndValidate() throws Exception {
        File tempConfigFile = File.createTempFile("agent-test", ".properties");
        tempConfigFile.deleteOnExit();
        AgentConfig.setTestUserConfigFile(tempConfigFile);

        try {
            String testToken = "testHeader.testPayload.testSignature";
            boolean saved = AgentConfig.saveUserToken(testToken);
            assertTrue(saved);

            File userConfigFile = AgentConfig.getUserConfigFile();
            assertTrue(userConfigFile.exists());

            AgentConfig loaded = AgentConfig.load();
            assertNotNull(loaded.getAgentToken());
            assertTrue(AgentConfig.isJwtStructured(loaded.getAgentToken()));
        } finally {
            AgentConfig.setTestUserConfigFile(null);
            tempConfigFile.delete();
        }
    }

    @Test
    void testStripQuotes() {
        assertEquals("abc.def.ghi", AgentConfig.stripQuotes("\"abc.def.ghi\""));
        assertEquals("abc.def.ghi", AgentConfig.stripQuotes("'abc.def.ghi'"));
        assertEquals("abc.def.ghi", AgentConfig.stripQuotes("abc.def.ghi"));
        assertNull(AgentConfig.stripQuotes(null));
        assertEquals("", AgentConfig.stripQuotes(""));
    }

    @Test
    void testIsJwtStructuredRejectsEllipsisAndWhitespace() {
        assertFalse(AgentConfig.isJwtStructured("eyJhbGci...truncated.token"));
        assertFalse(AgentConfig.isJwtStructured("eyJhbGci…truncated.token"));
        assertFalse(AgentConfig.isJwtStructured("eyJhbGci.payload with space.sig"));
        assertFalse(AgentConfig.isJwtStructured("eyJhbGci.payload\nnewline.sig"));
    }

    @Test
    void testInspectTokenMetadata() {
        // base64url for {"sub":"kumar","role":"AGENT"}
        // {"sub":"kumar","role":"AGENT"} -> eyJzdWIiOiJrdW1hciIsInJvbGUiOiJBR0VOVCJ9
        String mockJwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJrdW1hciIsInJvbGUiOiJBR0VOVCJ9.signature123";
        AgentConfig.JwtClaimsSummary summary = AgentConfig.inspectTokenMetadata(mockJwt);
        assertEquals("kumar", summary.subject);
        assertEquals("AGENT", summary.role);
        assertEquals(3, summary.partsCount);
        assertEquals(mockJwt.length(), summary.tokenLength);
    }
}
