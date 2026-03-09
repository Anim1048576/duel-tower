package com.example.dueltower.session.dto;

import java.util.List;

public record OwnedCardDto(
        String ownedCardId,
        String cardId,
        List<OwnedCardModifierDto> modifiers,
        Boolean strengthened,
        Boolean weakened,
        Boolean lockedInDeck,
        Boolean forgettable,
        String notForgettableReason
) {}
