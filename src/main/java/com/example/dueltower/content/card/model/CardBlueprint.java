package com.example.dueltower.content.card.model;

import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.Ids.CardDefId;

public interface CardBlueprint extends CardEffect {
    CardDefinition definition();

    /**
     * 덱 구성 시 카드별 허용 매수 제한 오버라이드.
     * - null 이면 기본 제한(현재 3장)을 사용한다.
     */
    default Integer maxDeckCopies() {
        return null;
    }

    default CardDefId defId() {
        return new CardDefId(id());
    }
}
