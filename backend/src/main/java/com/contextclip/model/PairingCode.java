package com.contextclip.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

public class PairingCode {
    private final String code;
    private final String username;
    private final Instant expiresAt;
    private final AtomicBoolean consumed = new AtomicBoolean(false);

    public PairingCode(String code, String username, Instant expiresAt) {
        this.code = code;
        this.username = username;
        this.expiresAt = expiresAt;
    }

    public String getCode() {
        return code;
    }

    public String getUsername() {
        return username;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isConsumed() {
        return consumed.get();
    }

    public boolean consume() {
        return consumed.compareAndSet(false, true);
    }
}
