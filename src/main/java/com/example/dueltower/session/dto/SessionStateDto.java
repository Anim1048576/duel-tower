package com.example.dueltower.session.dto;

import java.util.Map;

public record SessionStateDto(
        String sessionCode,
        String sessionId,
        long version,
        long seed,
        String nodeState,
        Map<String, PlayerStateDto> players,
        CombatStateDto combat,
        Map<String, CardInstanceDto> cards
) {}
