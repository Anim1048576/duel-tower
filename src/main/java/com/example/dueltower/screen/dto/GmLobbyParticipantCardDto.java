package com.example.dueltower.screen.dto;

import java.util.List;

public record GmLobbyParticipantCardDto(
        String slot,
        String name,
        String readyLabel,
        String readyTone,
        String characterSummary,
        String exSummary,
        String passiveSummary,
        String deckSummary,
        List<GmLobbyTagDto> detailTags
) {
    public GmLobbyParticipantCardDto {
        detailTags = detailTags == null ? List.of() : List.copyOf(detailTags);
    }
}
