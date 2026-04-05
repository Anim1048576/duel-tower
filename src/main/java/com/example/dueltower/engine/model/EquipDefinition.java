package com.example.dueltower.engine.model;

import java.util.List;

public record EquipDefinition(
        String id,
        String name,
        EquipSlot slot,
        String summary,
        String description,
        List<String> tags
) {
    public EquipDefinition {
        tags = (tags == null) ? List.of() : List.copyOf(tags);
    }
}
