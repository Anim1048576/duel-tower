package com.example.dueltower.character.dto;

public record CharacterCombatStatsPreviewRequest(
        Integer physical,
        Integer technique,
        Integer sense,
        Integer willpower
) {}
