package com.contextclip.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.contextclip.dto.AuthResponse;
import com.contextclip.dto.LoginRequest;
import com.contextclip.dto.MessageResponse;
import com.contextclip.dto.RegisterRequest;
import com.contextclip.exception.AuthValidationException;
import com.contextclip.exception.DuplicateUserException;
import com.contextclip.exception.InvalidCredentialsException;
import com.contextclip.model.User;
import com.contextclip.repository.UserRepository;
import com.contextclip.security.JwtService;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public MessageResponse register(RegisterRequest request) {
        if (request == null) {
            throw new AuthValidationException("Request body is required");
        }

        String username = request.username();
        if (username == null || username.trim().isEmpty()) {
            throw new AuthValidationException("Username is required and cannot be blank");
        }

        String password = request.password();
        if (password == null || password.trim().isEmpty()) {
            throw new AuthValidationException("Password is required and cannot be blank");
        }

        if (password.length() < 6) {
            throw new AuthValidationException("Password must be at least 6 characters long");
        }

        String trimmedUsername = username.trim();
        if (userRepository.existsByUsername(trimmedUsername)) {
            throw new DuplicateUserException("Username already exists: " + trimmedUsername);
        }

        String hashedPassword = passwordEncoder.encode(password);
        User user = new User(trimmedUsername, hashedPassword, "USER");
        userRepository.save(user);

        return new MessageResponse("User registered successfully");
    }

    public AuthResponse login(LoginRequest request) {
        if (request == null) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String username = request.username();
        String password = request.password();

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new AuthResponse(token, user.getUsername(), user.getRole());
    }

    public AuthResponse generateAgentTokenForUser(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new InvalidCredentialsException("Username is required");
        }
        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new InvalidCredentialsException("User not found: " + username));
        String token = jwtService.generateAgentToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), "AGENT");
    }

    public AuthResponse agentLogin(LoginRequest request) {
        if (request == null) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String username = request.username();
        String password = request.password();

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        User user = userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String token = jwtService.generateAgentToken(user.getUsername());
        return new AuthResponse(token, user.getUsername(), "AGENT");
    }
}
