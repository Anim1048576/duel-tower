package com.example.dueltower.session.dto;

public record DebugSoloCombatResponse(
        String sessionCode,
        String gmPlayerId,
        // Null in the default quick solo combat path. GM-NPC setup is not automatic here.
        String npcPlayerId,
        String gmToken,
        String playerToken,
        String redirectUrl
) {}
