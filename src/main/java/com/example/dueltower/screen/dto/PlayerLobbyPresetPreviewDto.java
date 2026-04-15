package com.example.dueltower.screen.dto;

import java.util.List;

public record PlayerLobbyPresetPreviewDto(
        String name,
        String summary,
        String characterLabel,
        String exLabel,
        List<PlayerLobbyPreviewItemDto> deckItems,
        List<PlayerLobbyPreviewItemDto> passiveItems
) {
    public PlayerLobbyPresetPreviewDto {
        deckItems = deckItems == null ? List.of() : List.copyOf(deckItems);
        passiveItems = passiveItems == null ? List.of() : List.copyOf(passiveItems);
    }
}
