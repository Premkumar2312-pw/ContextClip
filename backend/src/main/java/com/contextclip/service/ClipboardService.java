package com.contextclip.service;

import com.contextclip.model.ClipboardEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ClipboardService {

    private final AtomicLong idGenerator = new AtomicLong(0);
    private final List<ClipboardEntry> storage = new CopyOnWriteArrayList<>();

    public ClipboardEntry save(String content) {
        long id = idGenerator.incrementAndGet();
        ClipboardEntry entry = new ClipboardEntry(id, content);
        storage.add(entry);
        return entry;
    }

    public List<ClipboardEntry> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(storage));
    }

    public void clear() {
        storage.clear();
        idGenerator.set(0);
    }
}
