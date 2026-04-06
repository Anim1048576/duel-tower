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
    private static final String CONSUMABLE_TAG = "소모품";

    public ItemDefinition {
        tags = (tags == null) ? List.of() : List.copyOf(tags);
    }

    public boolean isConsumable() {
        return tags.contains(CONSUMABLE_TAG);
    }
}
