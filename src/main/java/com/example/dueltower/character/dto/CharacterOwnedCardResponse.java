package com.example.dueltower.character.dto;

import java.util.List;

public record CharacterOwnedCardResponse(
        String ownedCardId,
        String cardId,
        List<CharacterOwnedCardModifierResponse> modifiers,
        boolean strengthened,
        boolean weakened,
        boolean lockedInDeck,
        boolean forgettable,
        String notForgettableReason
) {
}
