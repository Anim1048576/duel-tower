package com.example.dueltower.session.service;

/**
 * Safe logging metadata for a granted SESSION_READABLE decision.
 * Carries only debugging identifiers such as source, session code, username, and playerId.
 * Raw token values must never be stored here.
 */
public record SessionAccessDecision(
        SessionAccessSource source,
        String sessionCode,
        String username,
        String playerId
) {
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
