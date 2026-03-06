package com.example.dueltower.character.dto;

import com.example.dueltower.character.domain.CharacterGender;

public record CharacterProfileRequest(
        String name,
        CharacterGender gender,
        Integer age,
        String wish,
        String disposition,
        String oneLiner,
        String story,
        Integer physical,
        Integer technique,
        Integer sense,
        Integer willpower,
        String trait1,
        String trait2,
        String ownedCards,
        String currentSkillDeck,
        String exCard
) {
}
