package com.example.dueltower.character.dto;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.session.dto.OwnedCardDto;

import java.util.List;

import java.sql.Timestamp;

/**
 * Public character read model for the normalized CharacterProfile aggregate.
 *
 * <p>The raw current skill deck is not exposed through this API. The equipped skill deck is stored as
 * ownedCardId-based rows in CharacterCurrentSkillDeckEntry, and this response exposes only
 * currentSkillDeckPreviewCardIds for UI display.</p>
 *
 * <p>ownedCards and exCard remain string JSON fields for API compatibility. ownedCardList and exCardId
 * are the preferred structured response fields. Their persistence source of truth is
 * CharacterOwnedCard/CharacterOwnedCardModifier and CharacterExLoadout, not CharacterProfile.</p>
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
        List<OwnedCardDto> ownedCardList,
        List<String> currentSkillDeckPreviewCardIds,
        String exCard,
        String exCardId,
        CombatStatsDto combatStats,
        Timestamp createDate,
        Timestamp updateDate
) {
}
