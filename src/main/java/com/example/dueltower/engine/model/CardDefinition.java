package com.example.dueltower.engine.model;

import com.example.dueltower.engine.model.Ids.CardDefId;

import java.util.EnumSet;

public record CardDefinition(
        CardDefId id,
        String name,
        CardType type,
        int cost,
        EnumSet<Keyword> keywords,
        Zone resolveTo,
        boolean token,
        String text
) {}