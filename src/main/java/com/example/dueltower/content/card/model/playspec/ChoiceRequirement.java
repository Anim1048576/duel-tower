package com.example.dueltower.content.card.model.playspec;

import java.util.List;

public record ChoiceRequirement(
        String id,
        String label,
        int minSelections,
        int maxSelections,
        List<ChoiceOption> options
) implements ExtraPlayRequirement {
    public ChoiceRequirement {
        if (minSelections < 0) {
            throw new IllegalArgumentException("minSelections must be >= 0");
        }
        if (maxSelections < minSelections) {
            throw new IllegalArgumentException("maxSelections must be >= minSelections");
        }
        options = options == null ? List.of() : List.copyOf(options);
    }
}
