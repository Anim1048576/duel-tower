package com.example.dueltower.engine.model;

/**
 * Keyword static definition (content).
 * - id should match the keyword token used in CardDefinition.keywords()
 * - For parameterized keywords, set parameterized=true and provide description template if needed.
 */
public record KeywordDefinition(
        String id,
        String name,
        boolean parameterized,
        String description,
        KeywordRole role,
        String parentKeywordId
) {
    public KeywordDefinition {
        if (role == null) {
            role = KeywordRole.STANDALONE;
        }
        if (parentKeywordId != null && parentKeywordId.isBlank()) {
            parentKeywordId = null;
        }
    }

    public KeywordDefinition(String id, String name, boolean parameterized, String description) {
        this(id, name, parameterized, description, KeywordRole.STANDALONE, null);
    }

    public boolean standalone() {
        return role == KeywordRole.STANDALONE;
    }

    public boolean attached() {
        return role == KeywordRole.ATTACHED;
    }
}
