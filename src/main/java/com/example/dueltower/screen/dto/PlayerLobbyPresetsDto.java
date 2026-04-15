package com.example.dueltower.screen.dto;

import java.util.List;

public record PlayerLobbyPresetsDto(
        List<PlayerLobbyPresetItemDto> items,
        Long selectedId,
        PlayerLobbyPresetPreviewDto preview
) {
    public PlayerLobbyPresetsDto {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
