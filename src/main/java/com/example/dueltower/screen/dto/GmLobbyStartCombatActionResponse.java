package com.example.dueltower.screen.dto;

/**
 * Result envelope for the GM lobby start-combat screen action.
 *
 * <p>The backend owns start-combat procedure details such as GM access restore,
 * blocked-state evaluation, version-mismatch retry, and already-active combat
 * transitions. The frontend consumes this response to navigate, refresh, or
 * display the returned disabledReason/message without re-running that flow.</p>
 */
public record GmLobbyStartCombatActionResponse(
        boolean success,
        String outcome,
        String message,
        DisabledReasonDto disabledReason,
        String nextRoute,
        String combatEntryHint,
        boolean gmAccessRestored,
        String restoredGmToken,
        boolean retryUsed,
        GmLobbyScreenResponse latestScreen
) {
}
