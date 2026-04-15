package com.example.dueltower.screen.dto;

import java.util.List;

/**
 * Curated reference options for PlayerLobby.
 * Character / EX / passive / owned-card labels and tags are resolved on the backend
 * so the frontend can render selection UI without rebuilding catalog joins.
 */
public record PlayerLobbyReferencesDto(
        List<PlayerLobbyOptionDto> characterOptions,
        List<PlayerLobbyOptionDto> exCardOptions,
        List<PlayerLobbyOptionDto> passiveOptions,
        List<PlayerLobbyOwnedCardOptionDto> ownedCardOptions
) {
    public PlayerLobbyReferencesDto {
        characterOptions = characterOptions == null ? List.of() : List.copyOf(characterOptions);
        exCardOptions = exCardOptions == null ? List.of() : List.copyOf(exCardOptions);
        passiveOptions = passiveOptions == null ? List.of() : List.copyOf(passiveOptions);
        ownedCardOptions = ownedCardOptions == null ? List.of() : List.copyOf(ownedCardOptions);
    }
}
