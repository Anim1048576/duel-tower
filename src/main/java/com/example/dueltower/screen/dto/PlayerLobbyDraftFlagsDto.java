package com.example.dueltower.screen.dto;

public record PlayerLobbyDraftFlagsDto(
        boolean dirty,
        boolean deckEditingLocked,
        boolean requiredFieldsMissing
) {
}
