package com.example.dueltower.screen.dto;

public record PlayerLobbyMeDto(
        String playerId,
        boolean ready,
        PlayerLobbyLoadoutDto loadout,
        PlayerLobbyMeSummaryDto summary,
        PlayerLobbyLoadoutDto draft,
        PlayerLobbyDraftFlagsDto draftFlags
) {
}
