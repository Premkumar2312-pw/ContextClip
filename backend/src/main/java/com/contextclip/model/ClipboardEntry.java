package com.contextclip.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

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

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "technology", nullable = false)
    private String technology;

    @Column(name = "category", nullable = false)
    private String category;

    // Phase 19 — multi-label classification fields (nullable for backward compat with pre-Phase-19 entries)
    @Column(name = "language")
    private String language;

    @Column(name = "technologies", length = 1000)
    private String technologies;   // comma-joined list, e.g. "SPRING_BOOT,JAVA"

    @Column(name = "categories", length = 1000)
    private String categories;     // comma-joined list, e.g. "DEVOPS,CONFIGURATION"

    @Column(name = "sensitive")
    private Boolean sensitive;

    @Column(name = "confidence")
    private Float confidence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    @JsonIgnore
    private User user;

    public ClipboardEntry() {
    }

    public ClipboardEntry(String content) {
        this(content, "TEXT", "UNKNOWN", "GENERAL");
    }

    public ClipboardEntry(String content, String type, String technology, String category) {
        this(content, type, technology, category, null);
    }

    public ClipboardEntry(String content, String type, String technology, String category, User user) {
        this.content = content;
        this.type = type != null ? type : "TEXT";
        this.technology = technology != null ? technology : "UNKNOWN";
        this.category = category != null ? category : "GENERAL";
        this.categories = this.category;
        if (this.technology != null && !this.technology.equals("UNKNOWN")) {
            this.technologies = this.technology;
        }
        this.sensitive = false;
        this.confidence = 1.0f;
        this.user = user;
        this.capturedAt = Instant.now();
    }

    public ClipboardEntry(Long id, String content, Instant capturedAt, String type, String technology, String category) {
        this(id, content, capturedAt, type, technology, category, null);
    }

    public ClipboardEntry(Long id, String content, Instant capturedAt, String type, String technology, String category, User user) {
        this.id = id;
        this.content = content;
        this.capturedAt = capturedAt != null ? capturedAt : Instant.now();
        this.type = type != null ? type : "TEXT";
        this.technology = technology != null ? technology : "UNKNOWN";
        this.category = category != null ? category : "GENERAL";
        this.categories = this.category;
        if (this.technology != null && !this.technology.equals("UNKNOWN")) {
            this.technologies = this.technology;
        }
        this.sensitive = false;
        this.confidence = 1.0f;
        this.user = user;
    }

    @PrePersist
    protected void onCreate() {
        if (this.capturedAt == null) {
            this.capturedAt = Instant.now();
        }
        if (this.type == null) {
            this.type = "TEXT";
        }
        if (this.technology == null) {
            this.technology = "UNKNOWN";
        }
        if (this.category == null) {
            this.category = "GENERAL";
        }
        if (this.technologies == null && this.technology != null && !this.technology.equals("UNKNOWN")) {
            this.technologies = this.technology;
        }
        if (this.categories == null && this.category != null) {
            this.categories = this.category;
        }
        if (this.sensitive == null) {
            this.sensitive = false;
        }
        if (this.confidence == null) {
            this.confidence = 1.0f;
        }
    }

    @PostLoad
    protected void onPostLoad() {
        if (this.categories == null && this.category != null) {
            this.categories = this.category;
        }
        if (this.technologies == null && this.technology != null && !this.technology.equals("UNKNOWN")) {
            this.technologies = this.technology;
        }
        if (this.sensitive == null) {
            this.sensitive = false;
        }
        if (this.confidence == null) {
            this.confidence = 1.0f;
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTechnology() {
        return technology;
    }

    public void setTechnology(String technology) {
        this.technology = technology;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    // Phase 19 getters/setters

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTechnologies() {
        return technologies;
    }

    public void setTechnologies(String technologies) {
        this.technologies = technologies;
    }

    public String getCategories() {
        return categories;
    }

    public void setCategories(String categories) {
        this.categories = categories;
    }

    public Boolean getSensitive() {
        return sensitive;
    }

    public Boolean isSensitive() {
        return sensitive;
    }

    public void setSensitive(Boolean sensitive) {
        this.sensitive = sensitive;
    }

    public Float getConfidence() {
        return confidence;
    }

    public void setConfidence(Float confidence) {
        this.confidence = confidence;
    }
}
