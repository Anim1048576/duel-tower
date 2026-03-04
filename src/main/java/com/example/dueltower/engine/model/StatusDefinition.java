package com.example.dueltower.engine.model;

import java.util.Set;

public record StatusDefinition(
        String id,
        String name,
        StatusKind kind,
        StatusScope scope,
        Set<StatusTag> tags,
        int priority,
        boolean persistsAfterCombat,
        String description
) {
    public StatusDefinition {
        tags = (tags == null) ? Set.of() : Set.copyOf(tags);
    }

    public boolean hasTag(StatusTag tag) {
        return tag != null && tags.contains(tag);
    }
}
