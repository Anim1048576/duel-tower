package com.example.dueltower.character.dto;

import com.example.dueltower.character.domain.CharacterGender;

import java.util.List;

import java.sql.Timestamp;

/**
 * Public character read model for the normalized CharacterProfile aggregate.
 *
 * <p>The raw current skill deck is not exposed through this API. The equipped skill deck is stored as
 * ownedCardId-based rows in CharacterCurrentSkillDeckEntry, and this response exposes only
 * currentSkillDeckPreviewCardIds for UI display.</p>
 *
 * <p>The legacy ownedCards/exCard string JSON fields are no longer exposed by this response.
 * ownedCardList and exCardId are the public loadout response fields. Legacy ownedCards/exCard
 * input remains accepted only through CharacterProfileRequest for compatibility. Their persistence
 * source of truth is CharacterOwnedCard/CharacterOwnedCardModifier and CharacterExLoadout,
 * not CharacterProfile.</p>
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
        List<CharacterOwnedCardResponse> ownedCardList,
        List<String> currentSkillDeckPreviewCardIds,
        String exCardId,
        CombatStatsDto combatStats,
        Timestamp createDate,
        Timestamp updateDate
) {
}
