package com.contextclip.controller;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.contextclip.dto.ClipboardExplanationResponse;
import com.contextclip.dto.ClipboardQuestionRequest;
import com.contextclip.dto.ClipboardQuestionResponse;
import com.contextclip.dto.ClipboardRequest;
import com.contextclip.dto.ClipboardResponse;
import com.contextclip.dto.ClipboardSummaryResponse;
import com.contextclip.exception.AiServiceException;
import com.contextclip.model.ClipboardEntry;
import com.contextclip.model.User;
import com.contextclip.repository.UserRepository;
import com.contextclip.service.ClipboardService;

@RestController
@RequestMapping("/api/clipboard")
public class ClipboardController {

    private final ClipboardService clipboardService;
    private final UserRepository userRepository;

    public ClipboardController(ClipboardService clipboardService, UserRepository userRepository) {
        this.clipboardService = clipboardService;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName()).orElse(null);
    }

    @PostMapping
    public ResponseEntity<?> createClipboardEntry(
            @RequestBody(required = false) ClipboardRequest request,
            Authentication authentication) {
        if (request == null || request.getContent() == null || request.getContent().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Bad Request",
                    "message", "Clipboard content cannot be empty or blank",
                    "status", 400
            ));
        }

        if (request.getContent().length() > ClipboardService.MAX_CLIPBOARD_CONTENT_LENGTH) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Bad Request",
                    "message", "Clipboard content exceeds maximum allowed length of " + ClipboardService.MAX_CLIPBOARD_CONTENT_LENGTH + " characters",
                    "status", 400
            ));
        }

        User user = getAuthenticatedUser(authentication);
        ClipboardEntry entry = clipboardService.save(request.getContent(), user);
        ClipboardResponse response = new ClipboardResponse(entry.getId(), "RECEIVED");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ClipboardEntry>> getAllClipboardEntries(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(clipboardService.getAll(user));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ClipboardEntry>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String technology,
            @RequestParam(required = false) String category,
            Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(clipboardService.search(user, q, type, technology, category));
    }

    @GetMapping("/{id}/explain")
    public ResponseEntity<?> explain(@PathVariable Long id, Authentication authentication) {
        if (id == null || id <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Bad Request",
                    "message", "Invalid clipboard entry ID",
                    "status", 400
            ));
        }

        User user = getAuthenticatedUser(authentication);
        try {
            ClipboardExplanationResponse response = clipboardService.explain(id, user);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Not Found",
                    "message", e.getMessage(),
                    "status", 404
            ));
        } catch (AiServiceException e) {
            return handleAiServiceException(e);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Bad Request",
                    "message", e.getMessage(),
                    "status", 400
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Unable to generate the AI response. Please try again.",
                    "status", 500
            ));
        }
    }

    @GetMapping("/{id}/summarize")
    public ResponseEntity<?> summarize(@PathVariable Long id, Authentication authentication) {
        if (id == null || id <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Bad Request",
                    "message", "Invalid clipboard entry ID",
                    "status", 400
            ));
        }

        User user = getAuthenticatedUser(authentication);
        try {
            ClipboardSummaryResponse response = clipboardService.summarize(id, user);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Not Found",
                    "message", e.getMessage(),
                    "status", 404
            ));
        } catch (AiServiceException e) {
            return handleAiServiceException(e);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Bad Request",
                    "message", e.getMessage(),
                    "status", 400
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Unable to generate the AI response. Please try again.",
                    "status", 500
            ));
        }
    }

    @PostMapping("/ask")
    public ResponseEntity<?> ask(
            @RequestBody(required = false) ClipboardQuestionRequest request,
            Authentication authentication) {
        if (request == null || request.question() == null || request.question().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Bad Request",
                    "message", "Question cannot be empty or blank",
                    "status", 400
            ));
        }

        if (request.question().length() > ClipboardService.MAX_QUESTION_LENGTH) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Bad Request",
                    "message", "Question exceeds maximum allowed length of " + ClipboardService.MAX_QUESTION_LENGTH + " characters",
                    "status", 400
            ));
        }

        User user = getAuthenticatedUser(authentication);
        try {
            ClipboardQuestionResponse response = clipboardService.askClipboard(request.question(), user);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Bad Request",
                    "message", e.getMessage(),
                    "status", 400
            ));
        } catch (AiServiceException e) {
            return handleAiServiceException(e);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal Server Error",
                    "message", "Unable to generate the AI response. Please try again.",
                    "status", 500
            ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClipboardEntry(@PathVariable Long id, Authentication authentication) {
        if (id == null || id <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Bad Request",
                    "message", "Invalid clipboard entry ID",
                    "status", 400
            ));
        }

        User user = getAuthenticatedUser(authentication);
        try {
            clipboardService.delete(id, user);
            return ResponseEntity.ok(Map.of(
                    "message", "Clipboard entry deleted successfully",
                    "status", 200
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "error", "Not Found",
                    "message", e.getMessage(),
                    "status", 404
            ));
        }
    }

    @DeleteMapping
    public ResponseEntity<?> clearClipboardHistory(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        clipboardService.clear(user);
        return ResponseEntity.ok(Map.of(
                "message", "Clipboard history cleared successfully",
                "status", 200
        ));
    }

    private ResponseEntity<?> handleAiServiceException(AiServiceException e) {
        int code = e.getStatusCode();
        if (code == 429) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "error", "Too Many Requests",
                    "message", "AI service rate limit reached. Please try again later.",
                    "status", 429
            ));
        }
        if (code == 401 || code == 403) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "Bad Gateway",
                    "message", "AI service authentication failed. Check the AI service configuration.",
                    "status", 502
            ));
        }
        if (code == 400) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "Bad Request",
                    "message", "AI service rejected the request. Please try again.",
                    "status", 400
            ));
        }
        if (code == 502 || code == 503) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "Bad Gateway",
                    "message", "AI service is unavailable. Please try again.",
                    "status", 502
            ));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "Internal Server Error",
                "message", "Unable to generate the AI response. Please try again.",
                "status", 500
        ));
    }
}

