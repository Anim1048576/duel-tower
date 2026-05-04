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
        String description,
        StatusVisibility visibility
) {
    public StatusDefinition {
        tags = (tags == null) ? Set.of() : Set.copyOf(tags);
        visibility = (visibility == null) ? StatusVisibility.PUBLIC : visibility;
    }

    public StatusDefinition(
            String id,
            String name,
            StatusKind kind,
            StatusScope scope,
            Set<StatusTag> tags,
            int priority,
            boolean persistsAfterCombat,
            String description
    ) {
        this(id, name, kind, scope, tags, priority, persistsAfterCombat, description, StatusVisibility.PUBLIC);
    }

    public boolean hasTag(StatusTag tag) {
        return tag != null && tags.contains(tag);
    }

    public boolean publicVisible() {
        return visibility == StatusVisibility.PUBLIC;
    }
}
