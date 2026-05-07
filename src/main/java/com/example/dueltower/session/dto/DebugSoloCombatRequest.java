package com.example.dueltower.session.dto;

public record DebugSoloCombatRequest(
        String gmPlayerId,
        // Deprecated for the default quick solo combat path; GM-NPC setup is handled separately.
        String npcName,
        Long playerCharacterId,
        // Deprecated for the default quick solo combat path; GM-NPC setup is handled separately.
        Long npcCharacterId
) {}
