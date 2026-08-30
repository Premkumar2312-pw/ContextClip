package com.contextclip.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.contextclip.model.User;
import com.contextclip.repository.UserRepository;

/**
 * Seeds a dedicated agent user account on startup if AGENT_USERNAME and
 * AGENT_PASSWORD environment variables are present.
 *
 * <p>This component is idempotent: if the agent account already exists it does
 * nothing. If either environment variable is absent or blank it skips silently
 * so the application always starts successfully regardless of agent configuration.
 *
 * <p>The agent user is stored with role "AGENT", allowing future controllers to
 * apply role-based access rules (e.g. {@code @PreAuthorize("hasRole('USER')")}
 * to restrict browser-only endpoints).
 */
@Component
public class AgentUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentUserInitializer.class);

    public static final String AGENT_USERNAME_ENV = "AGENT_USERNAME";
    public static final String AGENT_PASSWORD_ENV = "AGENT_PASSWORD";
    public static final String AGENT_ROLE = "AGENT";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AgentUserInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Reads an environment variable. Overridable in tests to inject controlled values
     * without mutating the real process environment.
     */
    protected String getEnv(String name) {
        return System.getenv(name);
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = getEnv(AGENT_USERNAME_ENV);
        String password = getEnv(AGENT_PASSWORD_ENV);

        if (username == null || username.isBlank()) {
            log.info("AGENT_USERNAME not set — skipping agent user seeding.");
            return;
        }

        if (password == null || password.isBlank()) {
            log.warn("AGENT_USERNAME is set but AGENT_PASSWORD is missing or blank — skipping agent user seeding.");
            return;
        }

        String trimmedUsername = username.trim();

        if (userRepository.existsByUsername(trimmedUsername)) {
            log.info("Agent user '{}' already exists — no action taken.", trimmedUsername);
            return;
        }

        String hashedPassword = passwordEncoder.encode(password);
        User agentUser = new User(trimmedUsername, hashedPassword, AGENT_ROLE);
        userRepository.save(agentUser);
        log.info("Agent user '{}' created with role '{}'.", trimmedUsername, AGENT_ROLE);
    }
}
