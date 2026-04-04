package com.example.dueltower.engine.model;

import java.util.List;

public record ItemDefinition(
        String id,
        String name,
        boolean battleUsable,
        String summary,
        String description,
        List<String> tags
) {
    public ItemDefinition {
        tags = (tags == null) ? List.of() : List.copyOf(tags);
    }
}
