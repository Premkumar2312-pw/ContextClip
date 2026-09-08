package com.contextclip.service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.contextclip.dto.AuthResponse;
import com.contextclip.dto.PairingResponse;
import com.contextclip.exception.AuthValidationException;
import com.contextclip.exception.InvalidCredentialsException;
import com.contextclip.model.PairingCode;
import com.contextclip.security.JwtService;

@Service
public class AgentPairingService {

    public static final long EXPIRATION_SECONDS = 300L; // 5 minutes
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JwtService jwtService;
    private final Map<String, PairingCode> codeStore = new ConcurrentHashMap<>();

    public AgentPairingService(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public PairingResponse generatePairingCode(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new AuthValidationException("Username is required for pairing");
        }

        cleanupExpired();

        byte[] randomBytes = new byte[24];
        RANDOM.nextBytes(randomBytes);
        String randomStr = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String code = "pair_" + randomStr;

        Instant expiresAt = Instant.now().plusSeconds(EXPIRATION_SECONDS);
        PairingCode pairingCode = new PairingCode(code, username.trim(), expiresAt);
        codeStore.put(code, pairingCode);

        String pairUrl = "contextclip://pair?code=" + code;
        return new PairingResponse(code, EXPIRATION_SECONDS, pairUrl);
    }

    public AuthResponse exchangePairingCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new InvalidCredentialsException("Pairing code is required");
        }

        PairingCode pairingCode = codeStore.get(code.trim());
        if (pairingCode == null) {
            throw new InvalidCredentialsException("Invalid or non-existent pairing code");
        }

        if (pairingCode.isExpired()) {
            codeStore.remove(code.trim());
            throw new InvalidCredentialsException("Pairing code has expired. Please generate a new one.");
        }

        if (!pairingCode.consume()) {
            throw new InvalidCredentialsException("Pairing code has already been used");
        }

        // Successfully consumed - remove from store
        codeStore.remove(code.trim());

        String username = pairingCode.getUsername();
        String agentToken = jwtService.generateAgentToken(username);

        return new AuthResponse(agentToken, username, "AGENT");
    }

    private void cleanupExpired() {
        codeStore.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    // Testing hook
    public void clearStore() {
        codeStore.clear();
    }
}
