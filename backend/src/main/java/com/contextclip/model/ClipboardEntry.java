package com.contextclip.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "clipboard_entries")
public class ClipboardEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    public ClipboardEntry() {
    }

    public ClipboardEntry(String content) {
        this.content = content;
        this.capturedAt = Instant.now();
    }

    public ClipboardEntry(Long id, String content, Instant capturedAt) {
        this.id = id;
        this.content = content;
        this.capturedAt = capturedAt != null ? capturedAt : Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.capturedAt == null) {
            this.capturedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(Instant capturedAt) {
        this.capturedAt = capturedAt;
    }
}
