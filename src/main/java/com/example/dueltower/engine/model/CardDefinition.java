package com.example.dueltower.engine.model;

import com.example.dueltower.engine.model.Ids.CardDefId;

import java.util.EnumSet;

public record CardDefinition(
        CardDefId id,
        String name,
        int cost,
        EnumSet<Keyword> keywords,
        String effectId,
        boolean token
) {}