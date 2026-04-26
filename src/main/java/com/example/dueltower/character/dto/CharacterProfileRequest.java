package com.example.dueltower.character.dto;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.session.dto.OwnedCardDto;

import java.util.List;

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
        List<String> hiddenTraitIds,
        String ownedCards,
        String exCard,
        List<OwnedCardDto> ownedCardList,
        String exCardId
) {
    /**
     * @deprecated Legacy JSON string request field. Use ownedCardList for create/update requests.
     */
    @Deprecated
    @Override
    public String ownedCards() {
        return ownedCards;
    }

    /**
     * @deprecated Legacy JSON string request field. Use exCardId for create/update requests.
     */
    @Deprecated
    @Override
    public String exCard() {
        return exCard;
    }
}
