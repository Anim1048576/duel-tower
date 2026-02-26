package com.example.dueltower.session.dto;

import java.util.List;

public record CombatStateDto(
        int round,
        List<String> turnOrder,
        int currentTurnIndex,
        String currentTurnPlayer
) {}
