package com.example.dueltower.session.dto;

public record DebugSoloCombatResponse(
        String sessionCode,
        String gmPlayerId,
        String npcPlayerId,
        String gmToken,
        String playerToken,
        String redirectUrl
) {}
