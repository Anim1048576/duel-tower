package com.example.dueltower.session.dto;

import java.util.List;

public record UpdateSessionDeckRequest(
        /** Canonical request field. */
        List<String> deckOwnedCardIds,
        /** Legacy compatibility field; prefer deckOwnedCardIds when both are provided. */
        List<String> deckCardIds
) {
    public List<String> requestedDeckOwnedCardIds() {
        if (deckOwnedCardIds != null) {
            return deckOwnedCardIds;
        }
        return deckCardIds;
    }
}
