package com.example.dueltower.screen.dto;

import java.util.List;

public record GmLobbyStartCombatDto(
        String recommendedStartPlayerId,
        DisabledReasonDto blockedReason,
        List<GmLobbySelectableStartPlayerDto> selectableStartPlayers
) {
    public GmLobbyStartCombatDto {
        selectableStartPlayers = selectableStartPlayers == null ? List.of() : List.copyOf(selectableStartPlayers);
    }
}
