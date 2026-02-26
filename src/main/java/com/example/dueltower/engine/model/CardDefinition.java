package com.example.dueltower.engine.model;

import com.example.dueltower.engine.model.Ids.CardDefId;

import java.util.Map;

/**
 * Card static definition (content).
 * keywords:
 * - Stored as raw strings to support custom keywords, e.g. "부동", "치명(2)".
 * - Parameterized keywords should use the form "{id}({number})".
 */
public record CardDefinition(
        CardDefId id,
        String name,
        CardType type,
        int cost,
        Map<String, Integer> keywords,
        Zone resolveTo,
        boolean token,
        String description
) {}
