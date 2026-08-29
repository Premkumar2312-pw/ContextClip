package com.contextclip.service;

import com.contextclip.client.AiServiceClient;
import com.contextclip.classifier.ClassificationResult;
import com.contextclip.classifier.ClipboardClassifier;
import com.contextclip.dto.ClipboardExplanationResponse;
import com.contextclip.model.ClipboardEntry;
import com.contextclip.repository.ClipboardRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ClipboardService {

    private final ClipboardRepository clipboardRepository;
    private final ClipboardClassifier clipboardClassifier;
    private final AiServiceClient aiServiceClient;

    public ClipboardService(
            ClipboardRepository clipboardRepository,
            ClipboardClassifier clipboardClassifier,
            AiServiceClient aiServiceClient) {
        this.clipboardRepository = clipboardRepository;
        this.clipboardClassifier = clipboardClassifier;
        this.aiServiceClient = aiServiceClient;
    }

    public ClipboardEntry save(String content) {
        ClassificationResult classification = clipboardClassifier.classify(content);
        ClipboardEntry entry = new ClipboardEntry(
                content,
                classification.type(),
                classification.technology(),
                classification.category()
        );
        return clipboardRepository.save(entry);
    }

    public List<ClipboardEntry> getAll() {
        return clipboardRepository.findAll();
    }

    public List<ClipboardEntry> search(String q, String type, String technology, String category) {
        String cleanQ = (q != null && !q.trim().isEmpty()) ? q.trim() : null;
        String cleanType = (type != null && !type.trim().isEmpty()) ? type.trim() : null;
        String cleanTech = (technology != null && !technology.trim().isEmpty()) ? technology.trim() : null;
        String cleanCat = (category != null && !category.trim().isEmpty()) ? category.trim() : null;

        return clipboardRepository.search(cleanQ, cleanType, cleanTech, cleanCat);
    }

    public ClipboardExplanationResponse explain(Long id) {
        ClipboardEntry entry = clipboardRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Clipboard entry with ID " + id + " not found"));

        if (entry.getContent() == null || entry.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Clipboard content cannot be empty or blank");
        }

        String prompt = buildExplanationPrompt(entry);
        String explanation = aiServiceClient.generateExplanation(prompt);
        return new ClipboardExplanationResponse(entry.getId(), explanation);
    }

    public String buildExplanationPrompt(ClipboardEntry entry) {
        return String.format("""
            You are an assistant inside ContextClip, an intelligent clipboard knowledge system for developers and students.
            Explain the following clipboard content clearly, accurately, and concisely.
            Detected Type: %s
            Detected Technology: %s
            Detected Category: %s

            Clipboard Content:
            %s
            """,
            entry.getType() != null ? entry.getType() : "UNKNOWN",
            entry.getTechnology() != null ? entry.getTechnology() : "UNKNOWN",
            entry.getCategory() != null ? entry.getCategory() : "UNKNOWN",
            entry.getContent()
        ).trim();
    }

    public void clear() {
        clipboardRepository.deleteAll();
    }
}
