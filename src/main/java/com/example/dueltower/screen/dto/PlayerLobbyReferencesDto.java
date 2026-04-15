package com.example.dueltower.screen.dto;

import java.util.List;

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
