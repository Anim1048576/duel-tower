package com.example.dueltower.screen.dto;

import java.util.List;

public record PlayerLobbyOwnedCardOptionDto(
        String ownedCardId,
        String cardId,
        String label,
        String subtitle,
        List<PlayerLobbyTagDto> tags
) {
    public PlayerLobbyOwnedCardOptionDto {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
