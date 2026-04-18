package com.example.dueltower.screen.dto;

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
