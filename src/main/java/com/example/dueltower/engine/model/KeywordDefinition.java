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
        String description
) {}
