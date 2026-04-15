package com.example.dueltower.content.deck.dto;

import com.example.dueltower.content.deck.domain.DeckType;

import java.util.List;

/**
 * Validation request keeps the existing cards payload and optionally allows
 * Screen API callers to validate against a draft type before saving.
 */
public record DeckValidationRequest(
        DeckType type,
        List<DeckCardSpec> cards
) {
    public static DeckValidationRequest fromReplaceCardsRequest(ReplaceDeckCardsRequest request) {
        if (request == null) {
            return null;
        }
        return new DeckValidationRequest(null, request.cards());
    }
}
