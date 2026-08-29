package com.contextclip.service;

import com.contextclip.model.ClipboardEntry;
import com.contextclip.repository.ClipboardRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClipboardService {

    private final ClipboardRepository clipboardRepository;

    public ClipboardService(ClipboardRepository clipboardRepository) {
        this.clipboardRepository = clipboardRepository;
    }

    public ClipboardEntry save(String content) {
        ClipboardEntry entry = new ClipboardEntry(content);
        return clipboardRepository.save(entry);
    }

    public List<ClipboardEntry> getAll() {
        return clipboardRepository.findAll();
    }

    public void clear() {
        clipboardRepository.deleteAll();
    }
}
