package com.example.dueltower.screen.dto;

public record PlayerLobbyMeSummaryDto(
        String readyLabel,
        String readyTone,
        String loadoutSummary,
        String draftSummary,
        String membershipSummary
) {
}
