package com.example.dueltower.session.dto;

public record OwnedCardDto(
        String cardId,
        boolean strengthened,
        boolean weakened,
        Boolean lockedInDeck,
        boolean forgettable,
        String notForgettableReason
) {}
