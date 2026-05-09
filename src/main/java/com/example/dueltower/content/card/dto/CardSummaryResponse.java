package com.example.dueltower.content.card.dto;

import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Zone;

import java.util.Map;

public record CardSummaryResponse(
        Ids.CardDefId id,
        String name,
        CardType type,
        int cost,
        Map<String, Integer> keywords,
        Zone resolveTo,
        boolean token,
        String description,
        String contentOwner
) {
    public static CardSummaryResponse of(CardDefinition definition, String contentOwner) {
        return new CardSummaryResponse(
                definition.id(),
                definition.name(),
                definition.type(),
                definition.cost(),
                definition.keywords(),
                definition.resolveTo(),
                definition.token(),
                definition.description(),
                contentOwner == null || contentOwner.isBlank() ? ContentOwnerIds.COMMON : contentOwner
        );
    }
}
