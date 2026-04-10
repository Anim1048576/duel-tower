package com.example.dueltower.session.service;

public record SessionAccessDecision(
        SessionAccessSource source,
        String sessionCode,
        String username,
        String playerId
) {
    public boolean gmAccess() {
        return source == SessionAccessSource.GM_TOKEN || source == SessionAccessSource.AUTHENTICATED_GM;
    }

    public boolean playerAccess() {
        return source == SessionAccessSource.PLAYER_TOKEN || source == SessionAccessSource.AUTHENTICATED_PLAYER;
    }

    public boolean tokenBased() {
        return source.tokenBased();
    }

    public boolean loginBased() {
        return !tokenBased();
    }

    public enum SessionAccessSource {
        GM_TOKEN(true),
        PLAYER_TOKEN(true),
        AUTHENTICATED_GM(false),
        AUTHENTICATED_PLAYER(false);

        private final boolean tokenBased;

        SessionAccessSource(boolean tokenBased) {
            this.tokenBased = tokenBased;
        }

        public boolean tokenBased() {
            return tokenBased;
        }
    }
}
