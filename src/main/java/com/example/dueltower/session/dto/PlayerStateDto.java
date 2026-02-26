package com.example.dueltower.session.dto;

import java.util.List;

public record PlayerStateDto(
        String playerId,
        List<String> deck,
        List<String> hand,
        List<String> grave,
        List<String> field,
        List<String> excluded,
        String exCard,
        boolean exOnCooldown,
        PendingDecisionDto pendingDecision,
        boolean swappedThisTurn,
        int cardsPlayedThisTurn,
        boolean usedExThisTurn,
        int handLimit,
        int fieldLimit
) {}
