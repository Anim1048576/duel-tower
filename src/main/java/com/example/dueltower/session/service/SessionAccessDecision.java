package com.example.dueltower.session.service;

public record SessionAccessDecision(
        SessionAccessSource source,
        String username,
        String playerId
) {
    public boolean gmAccess() {
        return source == SessionAccessSource.GM_TOKEN || source == SessionAccessSource.AUTHENTICATED_GM;
    }

    public boolean playerAccess() {
        return source == SessionAccessSource.PLAYER_TOKEN || source == SessionAccessSource.AUTHENTICATED_PLAYER;
    }

    public enum SessionAccessSource {
        GM_TOKEN,
        PLAYER_TOKEN,
        AUTHENTICATED_GM,
        AUTHENTICATED_PLAYER
    }
}
