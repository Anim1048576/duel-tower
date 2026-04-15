package com.example.dueltower.screen.dto;

/**
 * Player-specific server snapshot for PlayerLobby.
 * loadout is the last synced live loadout, draft is the server's current draft baseline,
 * and draftFlags describe server-known lock/missing-field state.
 * The frontend may derive local dirty/presentation state on top of this snapshot,
 * but it should not reinterpret loadout semantics or reference metadata.
 */
public record PlayerLobbyMeDto(
        String playerId,
        boolean ready,
        PlayerLobbyLoadoutDto loadout,
        PlayerLobbyMeSummaryDto summary,
        PlayerLobbyLoadoutDto draft,
        PlayerLobbyDraftFlagsDto draftFlags
) {
}
