package com.contextclip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.contextclip.config.AgentUserInitializer;
import com.contextclip.model.User;
import com.contextclip.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Unit tests for AgentUserInitializer.
 *
 * Environment variables are injected via system properties mapped to the same
 * names (using the withEnvironment helper) so no real env mutation is needed.
 * Because System.getenv() cannot be overridden in pure Java, we set real
 * environment variables for the duration of each test via reflection on
 * ProcessEnvironment.  Where that is unavailable (JDK 25 sealed internals)
 * we instead delegate env-reading to a package-visible overridable method and
 * test the logic directly through controlled subclasses.
 *
 * <p>To keep tests portable and JDK-version-agnostic, the tests use a
 * thin testable subclass that overrides {@code getEnv()} — a protected
 * helper added to {@link AgentUserInitializer} for exactly this purpose.
 */
@ExtendWith(MockitoExtension.class)
class AgentUserInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    /**
     * Testable subclass that allows environment variable values to be
     * injected without modifying the real process environment.
     */
    private static class TestableInitializer extends AgentUserInitializer {

        private final String agentUsername;
        private final String agentPassword;

        TestableInitializer(UserRepository repo, PasswordEncoder encoder,
                            String agentUsername, String agentPassword) {
            super(repo, encoder);
            this.agentUsername = agentUsername;
            this.agentPassword = agentPassword;
        }

        @Override
        protected String getEnv(String name) {
            return switch (name) {
                case AGENT_USERNAME_ENV -> agentUsername;
                case AGENT_PASSWORD_ENV -> agentPassword;
                default -> null;
            };
        }
    }

    // --- Tests ---

    @Test
    void whenBothEnvVarsSetAndUserAbsent_thenAgentUserCreatedWithAgentRole() throws Exception {
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-secret");
        when(userRepository.existsByUsername("contextclip-agent")).thenReturn(false);

        AgentUserInitializer initializer = new TestableInitializer(
                userRepository, passwordEncoder, "contextclip-agent", "secret123");
        initializer.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertEquals("contextclip-agent", saved.getUsername());
        assertEquals("hashed-secret", saved.getPassword());
        assertEquals("AGENT", saved.getRole());
    }

    @Test
    void whenUserAlreadyExists_thenNoSaveIsCalled() throws Exception {
        when(userRepository.existsByUsername("contextclip-agent")).thenReturn(true);

        AgentUserInitializer initializer = new TestableInitializer(
                userRepository, passwordEncoder, "contextclip-agent", "secret123");
        initializer.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void whenAgentUsernameIsNull_thenInitializerSkipsWithNoSideEffects() throws Exception {
        AgentUserInitializer initializer = new TestableInitializer(
                userRepository, passwordEncoder, null, "secret123");
        initializer.run(null);

        verify(userRepository, never()).existsByUsername(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void whenAgentUsernameIsBlank_thenInitializerSkipsWithNoSideEffects() throws Exception {
        AgentUserInitializer initializer = new TestableInitializer(
                userRepository, passwordEncoder, "   ", "secret123");
        initializer.run(null);

        verify(userRepository, never()).existsByUsername(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void whenAgentPasswordIsNull_thenInitializerSkipsWithNoSideEffects() throws Exception {
        AgentUserInitializer initializer = new TestableInitializer(
                userRepository, passwordEncoder, "contextclip-agent", null);
        initializer.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void whenAgentPasswordIsBlank_thenInitializerSkipsWithNoSideEffects() throws Exception {
        AgentUserInitializer initializer = new TestableInitializer(
                userRepository, passwordEncoder, "contextclip-agent", "  ");
        initializer.run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void whenBothEnvVarsSet_passwordIsEncodedBeforeStoring() throws Exception {
        when(passwordEncoder.encode("plaintext-password")).thenReturn("$2a$10$bcrypthash");
        when(userRepository.existsByUsername("agent")).thenReturn(false);

        AgentUserInitializer initializer = new TestableInitializer(
                userRepository, passwordEncoder, "agent", "plaintext-password");
        initializer.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        // Stored password must be the encoded form, never the plaintext
        assertEquals("$2a$10$bcrypthash", captor.getValue().getPassword());
    }
}
