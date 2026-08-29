package com.contextclip.controller;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.contextclip.dto.ClipboardExplanationResponse;
import com.contextclip.dto.ClipboardRequest;
import com.contextclip.dto.ClipboardResponse;
import com.contextclip.dto.ClipboardSummaryResponse;
import com.contextclip.exception.AiServiceException;
import com.contextclip.model.ClipboardEntry;
import com.contextclip.service.ClipboardService;

@RestController
@RequestMapping("/api/clipboard")
public class ClipboardController {

    private final ClipboardService clipboardService;

    public ClipboardController(ClipboardService clipboardService) {
        this.clipboardService = clipboardService;
    }

    @PostMapping
    public ResponseEntity<?> createClipboardEntry(@RequestBody(required = false) ClipboardRequest request) {
        if (request == null || request.getContent() == null || request.getContent().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Clipboard content cannot be empty or blank");
        }

        ClipboardEntry entry = clipboardService.save(request.getContent());
        ClipboardResponse response = new ClipboardResponse(entry.getId(), "RECEIVED");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ClipboardEntry>> getAllClipboardEntries() {
        return ResponseEntity.ok(clipboardService.getAll());
    }

    @GetMapping("/search")
    public ResponseEntity<List<ClipboardEntry>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String technology,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(clipboardService.search(q, type, technology, category));
    }

    @GetMapping("/{id}/explain")
    public ResponseEntity<?> explain(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid clipboard entry ID");
        }

        try {
            ClipboardExplanationResponse response = clipboardService.explain(id);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (AiServiceException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred");
        }
    }

    @GetMapping("/{id}/summarize")
    public ResponseEntity<?> summarize(@PathVariable Long id) {
        if (id == null || id <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid clipboard entry ID");
        }

        try {
            ClipboardSummaryResponse response = clipboardService.summarize(id);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (AiServiceException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred");
        }
    }
}
