package com.example.dueltower.content.card.dto;

import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Zone;

import java.util.Map;

public record CardDetailResponse(
        Ids.CardDefId id,
        String name,
        CardType type,
        int cost,
        Map<String, Integer> keywords,
        Zone resolveTo,
        boolean token,
        String description,
        CardPlaySpec playSpec
) {
    public static CardDetailResponse of(CardDefinition definition, CardPlaySpec playSpec) {
        return new CardDetailResponse(
                definition.id(),
                definition.name(),
                definition.type(),
                definition.cost(),
                definition.keywords(),
                definition.resolveTo(),
                definition.token(),
                definition.description(),
                playSpec == null ? CardPlaySpec.none() : playSpec
        );
    }
}
