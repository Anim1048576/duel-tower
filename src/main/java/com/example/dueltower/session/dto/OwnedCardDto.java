package com.example.dueltower.session.dto;

public record OwnedCardDto(
        String cardId,
        Boolean strengthened,
        Boolean weakened,
        Boolean lockedInDeck,
        Boolean forgettable,
        String notForgettableReason
) {}
