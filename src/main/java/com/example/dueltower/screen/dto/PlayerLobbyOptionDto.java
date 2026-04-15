package com.example.dueltower.screen.dto;

import java.util.List;

public record PlayerLobbyOptionDto(
        String id,
        String label,
        String subtitle,
        List<PlayerLobbyTagDto> tags
) {
    public PlayerLobbyOptionDto {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
