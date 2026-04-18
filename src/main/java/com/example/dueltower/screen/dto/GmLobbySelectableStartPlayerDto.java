package com.example.dueltower.screen.dto;

public record GmLobbySelectableStartPlayerDto(
        String playerId,
        String slot,
        String label,
        boolean ready
) {
}
