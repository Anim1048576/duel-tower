package com.example.dueltower.session.dto;

public record DebugSoloCombatRequest(
        String gmPlayerId,
        String npcName,
        Long playerCharacterId,
        Long npcCharacterId
) {}
