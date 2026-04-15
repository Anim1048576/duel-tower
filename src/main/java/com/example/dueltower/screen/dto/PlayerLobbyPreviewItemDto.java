package com.example.dueltower.screen.dto;

import java.util.List;

public record PlayerLobbyPreviewItemDto(
        String id,
        String label,
        String subtitle,
        List<PlayerLobbyTagDto> tags
) {
    public PlayerLobbyPreviewItemDto {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
