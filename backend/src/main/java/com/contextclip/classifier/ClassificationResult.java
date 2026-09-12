package com.contextclip.classifier;

import java.util.List;

/**
 * Multi-label deterministic classification result for a clipboard item.
 *
 * <ul>
 *   <li>{@code type}         – primary content type (CODE, SQL, URL, COMMAND, …)</li>
 *   <li>{@code technology}   – primary technology (backward compat, = first of {@code technologies} or "UNKNOWN")</li>
 *   <li>{@code category}     – primary category   (backward compat, = first of {@code categories}  or "GENERAL")</li>
 *   <li>{@code language}     – detected programming/markup language, or {@code null}</li>
 *   <li>{@code technologies} – all detected technologies/frameworks (may be empty)</li>
 *   <li>{@code categories}   – all detected categories (always ≥ 1)</li>
 *   <li>{@code sensitive}    – true when content contains a JWT, API key, private key, or password pattern</li>
 *   <li>{@code confidence}   – rule-based confidence in [0.0, 1.0]</li>
 * </ul>
 */
public record ClassificationResult(
        String type,
        String technology,
        String category,
        String language,
        List<String> technologies,
        List<String> categories,
        boolean sensitive,
        float confidence
) {

    // -------------------------------------------------------------------------
    // Factory helpers
    // -------------------------------------------------------------------------

    /** Simple single-label result (no language, not sensitive, full confidence). */
    public static ClassificationResult simple(String type, String tech, String cat) {
        List<String> techs = (tech != null && !tech.equals("UNKNOWN")) ? List.of(tech) : List.of();
        List<String> cats  = (cat  != null && !cat.equals("GENERAL"))  ? List.of(cat)  : List.of("GENERAL");
        return new ClassificationResult(type, tech != null ? tech : "UNKNOWN", cat != null ? cat : "GENERAL",
                null, techs, cats, false, 1.0f);
    }

    /** Multi-label result. {@code technologies} and {@code categories} must be non-null. */
    public static ClassificationResult of(
            String type,
            String language,
            List<String> technologies,
            List<String> categories,
            boolean sensitive,
            float confidence) {

        String tech = technologies.isEmpty() ? "UNKNOWN" : technologies.get(0);
        String cat  = categories.isEmpty()   ? "GENERAL"  : categories.get(0);
        return new ClassificationResult(type, tech, cat, language,
                List.copyOf(technologies), List.copyOf(categories), sensitive, confidence);
    }

    /** Marks content as sensitive (privacy guard). Type stays the same, confidence 1.0. */
    public static ClassificationResult sensitiveText(String type) {
        return new ClassificationResult(type, "UNKNOWN", "SENSITIVE",
                null, List.of(), List.of("SENSITIVE"), true, 1.0f);
    }
}
