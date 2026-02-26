package com.example.dueltower.content.card;

import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.Ids.CardDefId;

public interface CardBlueprint extends CardEffect {
    CardDefinition definition();

    default CardDefId defId() {
        return new CardDefId(id());
    }
}