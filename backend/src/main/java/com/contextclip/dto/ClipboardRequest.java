package com.contextclip.dto;

/**
 * Request body for {@code POST /api/clipboard}.
 *
 * <p>The {@code content} field is always required. All other fields are optional
 * and are populated by the Desktop Agent's local classifier (Phase 19). When
 * these optional fields are present, the backend trusts the agent's classification
 * and skips its own classifier. When they are absent (web / browser submission),
 * the backend classifies the content itself.
 */
public class ClipboardRequest {
    private String content;

    // Phase 19 — optional pre-classified fields from Desktop Agent
    private String type;
    private String technology;
    private String category;
    private String language;
    private String technologies;   // comma-joined, e.g. "SPRING_BOOT,JAVA"
    private String categories;     // comma-joined, e.g. "DEVOPS,CONFIGURATION"
    private Boolean sensitive;
    private Float confidence;

    public ClipboardRequest() {
    }

    public ClipboardRequest(String content) {
        this.content = content;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public void setSensitive(Boolean sensitive) {
        this.sensitive = sensitive;
    }

    public Float getConfidence() {
        return confidence;
    }

    public void setConfidence(Float confidence) {
        this.confidence = confidence;
    }

    /** Returns true if the agent has provided pre-classified metadata. */
    public boolean hasClassification() {
        return type != null && !type.isBlank();
    }
}
