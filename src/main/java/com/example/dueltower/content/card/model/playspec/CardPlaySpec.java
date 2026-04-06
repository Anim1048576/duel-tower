package com.example.dueltower.content.card.model.playspec;

import java.util.List;

public record CardPlaySpec(
        TargetSpec target,
        List<ExtraPlayRequirement> extraRequirements
) {
    public CardPlaySpec {
        target = target == null ? TargetSpec.none() : target;
        extraRequirements = extraRequirements == null ? List.of() : List.copyOf(extraRequirements);
    }

    public static CardPlaySpec none() {
        return new CardPlaySpec(TargetSpec.none(), List.of());
    }
}
