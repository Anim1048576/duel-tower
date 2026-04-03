package com.example.dueltower.session.dto;

public record ResetSessionRequest(
        Boolean keepPlayers,
        Boolean keepLoadouts,
        Long newSeed
) {
    public boolean keepPlayersOrDefault() {
        return keepPlayers == null || keepPlayers;
    }

    public boolean keepLoadoutsOrDefault() {
        return keepLoadouts == null || keepLoadouts;
    }
}
