package com.example.dueltower.screen.dto;

import com.example.dueltower.content.deck.dto.DeckValidationIssue;

import java.util.List;

public record DeckEditorValidationDto(
        boolean valid,
        int normalizedTotalCards,
        List<DeckValidationIssue> issues,
        boolean isStale,
        String validatedDraftSignature
) {
}
