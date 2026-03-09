package com.example.dueltower.content.card.model;

import java.util.Objects;

public record OwnedCardModifier(
        String modifierId,
        int value
) {
    public OwnedCardModifier {
        Objects.requireNonNull(modifierId, "modifierId is required");
        modifierId = modifierId.trim();
        if (modifierId.isEmpty()) {
            throw new IllegalArgumentException("modifierId is blank");
        }
    }
}
