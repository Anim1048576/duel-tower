package com.example.dueltower.screen.dto;

public record GmLobbyStartCombatActionRequest(
        Long expectedVersion,
        String playerId
) {
}
