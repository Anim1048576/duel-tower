package com.example.dueltower.session.dto;

import java.util.List;

public record PreviewSessionLoadoutDraftDto(
        Long characterId,
        List<String> passiveIds,
        List<String> deckOwnedCardIds,
        String exCardId
) {}
