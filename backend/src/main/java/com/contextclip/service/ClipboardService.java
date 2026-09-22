package com.contextclip.service;

import com.contextclip.client.AiServiceClient;
import com.contextclip.classifier.ClassificationResult;
import com.contextclip.classifier.ClipboardClassifier;
import com.contextclip.dto.ClipboardExplanationResponse;
import com.contextclip.dto.ClipboardQuestionResponse;
import com.contextclip.dto.ClipboardSummaryResponse;
import com.contextclip.model.ClipboardEntry;
import com.contextclip.model.User;
import com.contextclip.repository.ClipboardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ClipboardService {

    public static final int MAX_ASK_ENTRIES = 10;
    public static final int MAX_QUESTION_LENGTH = 2000;
    public static final int MAX_CLIPBOARD_CONTENT_LENGTH = 100_000;
    public static final int MAX_IMAGE_CONTENT_LENGTH = 10_000_000;

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "about", "all", "an", "and", "any", "are", "as", "at", "be", "been",
            "but", "by", "can", "clipboard", "code", "command", "commands", "copied",
            "could", "did", "do", "does", "for", "from", "give", "had", "has", "have",
            "how", "i", "if", "in", "into", "is", "it", "list", "me", "my", "no", "not",
            "of", "on", "or", "our", "saved", "should", "show", "some", "stored", "tell",
            "than", "that", "the", "their", "them", "then", "there", "these", "they",
            "this", "to", "was", "we", "were", "what", "when", "where", "which", "who",
            "why", "will", "with", "would", "you", "your"
    );

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
        return save(content, null);
    }

    public ClipboardEntry save(String content, User user) {
        ClassificationResult classification = clipboardClassifier.classify(content);
        ClipboardEntry entry = new ClipboardEntry(
                content,
                classification.type(),
                classification.technology(),
                classification.category(),
                user
        );
        // Phase 19 — populate multi-label fields
        entry.setLanguage(classification.language());
        String techsJoined = classification.technologies().isEmpty()
                ? (!classification.technology().equals("UNKNOWN") ? classification.technology() : null)
                : String.join(",", classification.technologies());
        entry.setTechnologies(techsJoined);
        String catsJoined = classification.categories().isEmpty()
                ? classification.category()
                : String.join(",", classification.categories());
        entry.setCategories(catsJoined);
        entry.setSensitive(classification.sensitive());
        entry.setConfidence(classification.confidence());
        return clipboardRepository.save(entry);
    }

    /**
     * Saves a clipboard entry using pre-classified metadata provided by the Desktop Agent.
     * Falls back to running the backend classifier when the request has no classification.
     */
    public ClipboardEntry save(String content, com.contextclip.dto.ClipboardRequest request, User user) {
        if (request == null || !request.hasClassification()) {
            return save(content, user);
        }
        // Trust the agent's pre-classified values
        String type     = request.getType()       != null ? request.getType()       : "PLAIN_TEXT";
        String tech     = request.getTechnology()  != null ? request.getTechnology()  : "UNKNOWN";
        String cat      = request.getCategory()    != null ? request.getCategory()    : "GENERAL";
        ClipboardEntry entry = new ClipboardEntry(content, type, tech, cat, user);
        entry.setLanguage(request.getLanguage());
        String techs = (request.getTechnologies() != null && !request.getTechnologies().isBlank())
                ? request.getTechnologies()
                : (!tech.equals("UNKNOWN") ? tech : null);
        entry.setTechnologies(techs);
        String cats = (request.getCategories() != null && !request.getCategories().isBlank())
                ? request.getCategories()
                : cat;
        entry.setCategories(cats);
        entry.setSensitive(request.getSensitive() != null ? request.getSensitive() : false);
        entry.setConfidence(request.getConfidence() != null ? request.getConfidence() : 1.0f);
        return clipboardRepository.save(entry);
    }

    public List<ClipboardEntry> getAll() {
        return clipboardRepository.findAll();
    }

    public List<ClipboardEntry> getAll(User user) {
        if (user == null) {
            return clipboardRepository.findAll();
        }
        return clipboardRepository.findByUserOrderByCapturedAtDesc(user);
    }

    public List<ClipboardEntry> search(String q, String type, String technology, String category) {
        String cleanQ = (q != null && !q.trim().isEmpty()) ? q.trim() : null;
        String cleanType = (type != null && !type.trim().isEmpty()) ? type.trim() : null;
        String cleanTech = (technology != null && !technology.trim().isEmpty()) ? technology.trim() : null;
        String cleanCat = (category != null && !category.trim().isEmpty()) ? category.trim() : null;

        return clipboardRepository.search(cleanQ, cleanType, cleanTech, cleanCat);
    }

    public List<ClipboardEntry> search(User user, String q, String type, String technology, String category) {
        if (user == null) {
            return search(q, type, technology, category);
        }
        String cleanQ = (q != null && !q.trim().isEmpty()) ? q.trim() : null;
        String cleanType = (type != null && !type.trim().isEmpty()) ? type.trim() : null;
        String cleanTech = (technology != null && !technology.trim().isEmpty()) ? technology.trim() : null;
        String cleanCat = (category != null && !category.trim().isEmpty()) ? category.trim() : null;

        return clipboardRepository.search(user, cleanQ, cleanType, cleanTech, cleanCat);
    }

    public ClipboardExplanationResponse explain(Long id) {
        return explain(id, null);
    }

    public ClipboardExplanationResponse explain(Long id, User user) {
        ClipboardEntry entry = user == null
                ? clipboardRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Clipboard entry with ID " + id + " not found"))
                : clipboardRepository.findByIdAndUser(id, user).orElseThrow(() -> new NoSuchElementException("Clipboard entry with ID " + id + " not found"));

        if (entry.getContent() == null || entry.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Clipboard content cannot be empty or blank");
        }

        String explanation;
        if ("IMAGE".equalsIgnoreCase(entry.getType()) || (entry.getContent() != null && entry.getContent().startsWith("data:image/"))) {
            String prompt = "Describe and explain what this image shows clearly, accurately, and concisely for a developer or student. Detail any text, diagrams, code, UI elements, or architecture shown in the image.";
            explanation = aiServiceClient.generateVisionResponse(prompt, entry.getContent());
        } else {
            String prompt = buildExplanationPrompt(entry);
            explanation = aiServiceClient.generateResponse(prompt);
        }
        return new ClipboardExplanationResponse(entry.getId(), explanation);
    }

    public ClipboardSummaryResponse summarize(Long id) {
        return summarize(id, null);
    }

    public ClipboardSummaryResponse summarize(Long id, User user) {
        ClipboardEntry entry = user == null
                ? clipboardRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Clipboard entry with ID " + id + " not found"))
                : clipboardRepository.findByIdAndUser(id, user).orElseThrow(() -> new NoSuchElementException("Clipboard entry with ID " + id + " not found"));

        if (entry.getContent() == null || entry.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Clipboard content cannot be empty or blank");
        }

        String summary;
        if ("IMAGE".equalsIgnoreCase(entry.getType()) || (entry.getContent() != null && entry.getContent().startsWith("data:image/"))) {
            String prompt = "Provide a concise 1-2 sentence summary of what this image shows.";
            summary = aiServiceClient.generateVisionResponse(prompt, entry.getContent());
        } else {
            String prompt = buildSummarizationPrompt(entry);
            summary = aiServiceClient.generateResponse(prompt);
        }
        return new ClipboardSummaryResponse(entry.getId(), summary);
    }

    public ClipboardQuestionResponse askClipboard(String question) {
        return askClipboard(question, null);
    }

    public ClipboardQuestionResponse askClipboard(String question, User user) {
        if (question == null || question.trim().isEmpty()) {
            throw new IllegalArgumentException("Question cannot be empty or blank");
        }

        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new IllegalArgumentException("Question exceeds maximum allowed length of " + MAX_QUESTION_LENGTH + " characters");
        }

        String cleanQuestion = question.trim();
        String lowerQ = cleanQuestion.toLowerCase();
        boolean hasImageKeyword = lowerQ.matches(".*\\b(image|screenshot|picture|diagram|photo)\\b.*");
        boolean hasRecentTarget = lowerQ.matches(".*\\b(latest|recent|most\\s+recent|last|this)\\b.*");

        List<ClipboardEntry> relevantEntries = findRelevantEntries(cleanQuestion, user);

        // Check if we should route to AI Vision
        ClipboardEntry targetImageEntry = null;
        if (hasImageKeyword) {
            targetImageEntry = relevantEntries.stream()
                    .filter(this::isImageEntry)
                    .findFirst()
                    .orElse(null);
            if (targetImageEntry == null && user != null) {
                targetImageEntry = clipboardRepository.findByUserOrderByCapturedAtDesc(user).stream()
                        .filter(this::isImageEntry)
                        .findFirst()
                        .orElse(null);
            } else if (targetImageEntry == null) {
                targetImageEntry = clipboardRepository.findAll().stream()
                        .filter(this::isImageEntry)
                        .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                        .findFirst()
                        .orElse(null);
            }
        } else if (hasRecentTarget && !relevantEntries.isEmpty() && isImageEntry(relevantEntries.get(0))) {
            targetImageEntry = relevantEntries.get(0);
        }

        if (targetImageEntry != null) {
            String visionPrompt = "The user asks: \"" + cleanQuestion + "\". Analyze this image from their clipboard and answer their question directly, concisely, and accurately.";
            String answer = aiServiceClient.generateVisionResponse(visionPrompt, targetImageEntry.getContent());
            return new ClipboardQuestionResponse(answer, List.of(targetImageEntry.getId()));
        }

        if (hasImageKeyword && targetImageEntry == null) {
            return new ClipboardQuestionResponse("No image clipboard entries found in your history to answer this question.", List.of());
        }

        // Check if the prompt is asking about clipboard content
        if (isClipboardAware(cleanQuestion)) {
            if (relevantEntries.isEmpty()) {
                return new ClipboardQuestionResponse("No clipboard entries found in your history to answer this question.", List.of());
            }

            String prompt = buildAskPrompt(cleanQuestion, relevantEntries);
            String answer = aiServiceClient.generateResponse(prompt);

            List<Long> sources = relevantEntries.stream()
                    .map(ClipboardEntry::getId)
                    .toList();

            return new ClipboardQuestionResponse(answer, sources);
        }

        // General AI prompt — no clipboard context attached
        String generalPrompt = String.format("""
            You are the ContextClip assistant, an intelligent AI helper for developers and students.
            Answer the following question clearly, accurately, and concisely. Use GitHub Flavored Markdown for formatting, code snippets, lists, or tables where appropriate.

            Question:
            %s
            """, cleanQuestion).trim();

        String answer = aiServiceClient.generateResponse(generalPrompt);
        return new ClipboardQuestionResponse(answer, List.of());
    }

    public boolean isClipboardAware(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String lower = question.toLowerCase().trim();

        // Check for specific entry references e.g. "entry #12", "entry 5"
        if (lower.matches(".*\\bentry\\s*#?\\d+\\b.*")) {
            return true;
        }

        // Check for explicit clipboard / copied / saved / history / entry terms
        if (lower.matches(".*\\b(clipboard|history|copied|copi(?:ed)?|paste|pasted|saved|entries|entry|snippet|snippets)\\b.*")) {
            return true;
        }

        // Check for personal possessive references to user's code, commands, or data
        if (lower.matches(".*\\b(my|our)\\s+(code|commands?|sql|queries|query|docker|git|text|image|images|screenshot|screenshots|history|data|items?|logs?)\\b.*")) {
            return true;
        }

        // Check for personal question constructs e.g. "have I copied", "did I save", "what did I", "what do I have"
        if (lower.matches(".*\\b(have|did|do|can)\\s+(i|we)\\b.*")) {
            return true;
        }

        // Check for relative phrases e.g. "in my ...", "from my ...", "of my ..."
        if (lower.matches(".*\\b(in|from|of)\\s+(my|our)\\b.*")) {
            return true;
        }

        // Check for demonstrative references e.g. "this code", "this snippet", "this command", "this error", "these entries"
        if (lower.matches(".*\\bthis\\s+(code|snippet|command|query|error|message|exception|image|screenshot|entry|text)\\b.*")) {
            return true;
        }
        if (lower.matches(".*\\bthese\\s+(entries|snippets|commands|queries|errors)\\b.*")) {
            return true;
        }

        // Check for "the code", "the command", "the snippet", "the error", "the query"
        if (lower.matches(".*\\bthe\\s+(code|snippets?|commands?|queries|query|error|exception|docker commands?|git commands?)\\b.*")) {
            return true;
        }

        // Check for "all entries", "which entries", "what entries"
        if (lower.matches(".*\\b(all|which|what)\\s+entries\\b.*")) {
            return true;
        }

        // Check for image references
        if (lower.matches(".*\\b(image|screenshot|picture|diagram|photo)\\b.*")) {
            return true;
        }

        // Check for latest/recent references when combined with actions
        if (lower.matches(".*\\b(latest|recent|most\\s+recent|last)\\b.*")) {
            return true;
        }

        return false;
    }

    private boolean isImageEntry(ClipboardEntry e) {
        if (e == null) return false;
        return "IMAGE".equalsIgnoreCase(e.getType()) ||
                (e.getContent() != null && e.getContent().startsWith("data:image/"));
    }

    public List<ClipboardEntry> findRelevantEntries(String question) {
        return findRelevantEntries(question, null);
    }

    public List<ClipboardEntry> findRelevantEntries(String question, User user) {
        List<String> keywords = extractKeywords(question);
        Map<Long, ClipboardEntry> matches = new LinkedHashMap<>();

        if (user != null) {
            for (String keyword : keywords) {
                for (ClipboardEntry e : clipboardRepository.search(user, keyword, null, null, null)) {
                    matches.putIfAbsent(e.getId(), e);
                }
                for (ClipboardEntry e : clipboardRepository.search(user, null, null, keyword, null)) {
                    matches.putIfAbsent(e.getId(), e);
                }
                for (ClipboardEntry e : clipboardRepository.search(user, null, keyword, null, null)) {
                    matches.putIfAbsent(e.getId(), e);
                }
                for (ClipboardEntry e : clipboardRepository.search(user, null, null, null, keyword)) {
                    matches.putIfAbsent(e.getId(), e);
                }
            }

            if (matches.isEmpty()) {
                for (ClipboardEntry e : clipboardRepository.findByUserOrderByCapturedAtDesc(user)) {
                    matches.putIfAbsent(e.getId(), e);
                }
            }
        } else {
            for (String keyword : keywords) {
                for (ClipboardEntry e : clipboardRepository.search(keyword, null, null, null)) {
                    matches.putIfAbsent(e.getId(), e);
                }
                for (ClipboardEntry e : clipboardRepository.search(null, null, keyword, null)) {
                    matches.putIfAbsent(e.getId(), e);
                }
                for (ClipboardEntry e : clipboardRepository.search(null, keyword, null, null)) {
                    matches.putIfAbsent(e.getId(), e);
                }
                for (ClipboardEntry e : clipboardRepository.search(null, null, null, keyword)) {
                    matches.putIfAbsent(e.getId(), e);
                }
            }

            if (matches.isEmpty()) {
                for (ClipboardEntry e : clipboardRepository.search(null, null, null, null)) {
                    matches.putIfAbsent(e.getId(), e);
                }
            }
        }

        return matches.values().stream()
                .limit(MAX_ASK_ENTRIES)
                .toList();
    }

    public List<String> extractKeywords(String question) {
        if (question == null || question.trim().isEmpty()) {
            return List.of();
        }

        String normalized = question.toLowerCase().replaceAll("[^a-z0-9\\s_-]", " ");
        return Arrays.stream(normalized.split("\\s+"))
                .map(String::trim)
                .filter(word -> word.length() >= 2)
                .filter(word -> !STOP_WORDS.contains(word))
                .distinct()
                .collect(Collectors.toList());
    }

    public String buildAskPrompt(String question, List<ClipboardEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the ContextClip assistant, an intelligent clipboard knowledge system for developers and students.\n\n");
        sb.append("Answer the user's question using only the provided clipboard context.\n\n");
        sb.append("User Question:\n");
        sb.append(question).append("\n\n");
        sb.append("Retrieved Clipboard Context:\n");

        if (entries == null || entries.isEmpty()) {
            sb.append("(No relevant clipboard entries found in history)\n\n");
        } else {
            for (ClipboardEntry entry : entries) {
                String content = entry.getContent() != null ? entry.getContent() : "";
                if ("IMAGE".equalsIgnoreCase(entry.getType()) || content.startsWith("data:image/")) {
                    content = "[Image Entry: captured on " + entry.getCapturedAt() + "]";
                } else if (content.length() > 800) {
                    content = content.substring(0, 800) + "\n...[content truncated]";
                }
                sb.append(String.format("""
                    Clipboard Entry #%d
                    Type: %s
                    Technology: %s
                    Category: %s
                    Content:
                    %s

                    """,
                    entry.getId(),
                    entry.getType() != null ? entry.getType() : "UNKNOWN",
                    entry.getTechnology() != null ? entry.getTechnology() : "UNKNOWN",
                    entry.getCategory() != null ? entry.getCategory() : "UNKNOWN",
                    content
                ));
            }
        }

        sb.append("""
            Instructions:
            - Use only the provided clipboard context.
            - Do not invent clipboard entries or facts not present in the context.
            - If the provided clipboard context does not contain enough relevant information to answer the question, clearly state that the clipboard history does not contain enough relevant information.
            - Give a concise, accurate, and useful answer.
            - Mention relevant commands, code snippets, or content where appropriate.
            """);

        String prompt = sb.toString().trim();
        if (prompt.length() > 9500) {
            prompt = prompt.substring(0, 9500) + "\n...[context truncated]";
        }
        return prompt;
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

    public String buildSummarizationPrompt(ClipboardEntry entry) {
        return String.format("""
            You are an assistant inside ContextClip, an intelligent clipboard knowledge system for developers and students.
            Summarize the following clipboard content accurately, clearly, and concisely.
            Preserve important technical meaning, do not invent information, and use plain language appropriate for developers and students.
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

    @Transactional
    public void delete(Long id) {
        if (!clipboardRepository.existsById(id)) {
            throw new NoSuchElementException("Clipboard entry with ID " + id + " not found");
        }
        clipboardRepository.deleteById(id);
    }

    @Transactional
    public void delete(Long id, User user) {
        if (user == null) {
            delete(id);
            return;
        }
        ClipboardEntry entry = clipboardRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new NoSuchElementException("Clipboard entry with ID " + id + " not found"));
        clipboardRepository.delete(entry);
    }

    @Transactional
    public void clear() {
        clipboardRepository.deleteAll();
    }

    @Transactional
    public void clear(User user) {
        if (user == null) {
            clear();
            return;
        }
        clipboardRepository.deleteByUser(user);
    }
}

