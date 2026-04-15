package com.example.dueltower.screen.dto;

import java.util.List;

public record PlayerLobbyLoadoutDto(
        Long characterId,
        String characterLabel,
        List<String> deckOwnedCardIds,
        String exCardId,
        String exLabel,
        List<String> passiveIds,
        int deckCount,
        int passiveCount
) {
    public PlayerLobbyLoadoutDto {
        deckOwnedCardIds = deckOwnedCardIds == null ? List.of() : List.copyOf(deckOwnedCardIds);
        passiveIds = passiveIds == null ? List.of() : List.copyOf(passiveIds);
    }
}
