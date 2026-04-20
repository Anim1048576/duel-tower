package com.example.dueltower.session.dto;

import java.util.List;

public record PreviewSessionLoadoutRequest(
        Long characterId,
        List<String> passiveIds,
        List<String> deckOwnedCardIds,
        String exCardId
) {}
