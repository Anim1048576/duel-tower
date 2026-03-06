package com.example.dueltower.character.dto;

import com.example.dueltower.character.domain.CharacterGender;

import java.sql.Timestamp;

public record CharacterProfileResponse(
        Long id,
        String name,
        CharacterGender gender,
        Integer age,
        String wish,
        String disposition,
        String oneLiner,
        String story,
        int physical,
        int technique,
        int sense,
        int willpower,
        String trait1,
        String trait2,
        String ownedCards,
        String currentSkillDeck,
        String exCard,
        CombatStatsDto combatStats,
        Timestamp createDate,
        Timestamp updateDate
) {
}
