package com.example.dueltower.screen.dto;

import java.util.List;

/**
 * GM lobby start-combat snapshot.
 *
 * <p>The backend decides blockedReason and recommendedStartPlayerId from the
 * current session state. The frontend only renders and submits the chosen
 * playerId through the declared action contract.</p>
 */
public record GmLobbyStartCombatDto(
        String recommendedStartPlayerId,
        DisabledReasonDto blockedReason,
        List<GmLobbySelectableStartPlayerDto> selectableStartPlayers
) {
    public GmLobbyStartCombatDto {
        selectableStartPlayers = selectableStartPlayers == null ? List.of() : List.copyOf(selectableStartPlayers);
    }
}
