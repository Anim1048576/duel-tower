package com.example.dueltower.session.dto;

import java.util.List;

public record UpdateSessionLoadoutRequest(
        Long characterId,
        List<String> passiveIds,
        List<String> deckOwnedCardIds,
        String exCardId
) {}
