package com.contextclip.agent;

/**
 * Represents the current operational and connectivity status of the Desktop Agent.
 */
public enum ConnectionStatus {
    CONNECTED("Connected"),
    DISCONNECTED("Disconnected (Backend unavailable)"),
    UNAUTHORIZED("Unauthorized (Invalid/Expired AGENT_TOKEN)"),
    FORBIDDEN("Forbidden (ROLE_AGENT not permitted)"),
    RATE_LIMITED("Rate Limited (Too Many Requests)"),
    SERVER_ERROR("Server Error (Backend 5xx)"),
    PAUSED("Monitoring Paused");

    private final String displayName;

    ConnectionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
