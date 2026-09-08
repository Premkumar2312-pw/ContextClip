package com.contextclip.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.contextclip.dto.AuthResponse;
import com.contextclip.dto.PairingExchangeRequest;
import com.contextclip.dto.PairingResponse;
import com.contextclip.service.AgentPairingService;

@RestController
@RequestMapping("/api/agent/pairing")
public class AgentPairingController {

    private final AgentPairingService agentPairingService;

    public AgentPairingController(AgentPairingService agentPairingService) {
        this.agentPairingService = agentPairingService;
    }

    @PostMapping
    public ResponseEntity<PairingResponse> createPairingCode(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        PairingResponse response = agentPairingService.generatePairingCode(authentication.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/exchange")
    public ResponseEntity<AuthResponse> exchangePairingCode(@RequestBody(required = false) PairingExchangeRequest request) {
        if (request == null || request.code() == null || request.code().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        AuthResponse response = agentPairingService.exchangePairingCode(request.code());
        return ResponseEntity.ok(response);
    }
}
