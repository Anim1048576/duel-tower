package com.example.dueltower.character.dto;

import com.example.dueltower.character.domain.CharacterGender;

import java.util.List;

import java.sql.Timestamp;

/**
 * Public character read model.
 *
 * <p>Raw CharacterProfile.currentSkillDeck is intentionally not exposed because it can contain either
 * cardId or ownedCardId values depending on the internal write path. UI/API clients should render
 * currentSkillDeckPreviewCardIds, which is resolved on the server as cardId-based preview data.</p>
 */
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
        List<String> hiddenTraitIds,
        String ownedCards,
        List<String> currentSkillDeckPreviewCardIds,
        String exCard,
        CombatStatsDto combatStats,
        Timestamp createDate,
        Timestamp updateDate
) {
}
