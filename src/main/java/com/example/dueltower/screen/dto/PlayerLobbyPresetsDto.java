package com.example.dueltower.screen.dto;

import java.util.List;

/**
 * Server-selected preset list and preview snapshot for PlayerLobby.
 * preview is only guaranteed for the current server-selected preset snapshot;
 * the frontend may mark it locally stale when the user changes selection before refresh.
 */
public record PlayerLobbyPresetsDto(
        List<PlayerLobbyPresetItemDto> items,
        Long selectedId,
        PlayerLobbyPresetPreviewDto preview
) {
    public PlayerLobbyPresetsDto {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
