package com.example.dueltower.screen.dto;

import java.util.List;

/**
 * Participant card already curated for the GM lobby UI.
 *
 * <p>{@code playerId} is the stable action target. {@code name} is display-only.
 * characterSummary / exSummary / passiveSummary / deckSummary / detailTags are
 * server-owned presentation data, not frontend-derived estimates.</p>
 */
public record GmLobbyParticipantCardDto(
        String slot,
        String playerId,
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
