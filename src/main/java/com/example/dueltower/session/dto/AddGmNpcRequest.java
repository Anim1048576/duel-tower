package com.example.dueltower.session.dto;

import java.util.List;

public record AddGmNpcRequest(
        String name,
        Long characterId,
        List<String> requestedDeckOwnedCardIds,
        String exCardId,
        List<OwnedCardDto> ownedCards
) {}
