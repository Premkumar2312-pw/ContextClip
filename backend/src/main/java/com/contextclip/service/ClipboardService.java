package com.contextclip.service;

import com.contextclip.classifier.ClassificationResult;
import com.contextclip.classifier.ClipboardClassifier;
import com.contextclip.model.ClipboardEntry;
import com.contextclip.repository.ClipboardRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClipboardService {

    private final ClipboardRepository clipboardRepository;
    private final ClipboardClassifier clipboardClassifier;

    public ClipboardService(ClipboardRepository clipboardRepository, ClipboardClassifier clipboardClassifier) {
        this.clipboardRepository = clipboardRepository;
        this.clipboardClassifier = clipboardClassifier;
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

    public void clear() {
        clipboardRepository.deleteAll();
    }
}
