package com.example.dueltower.engine.core.effect.card;

import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.model.Ids.CardInstId;

import java.util.List;

public interface CardEffect extends PersistentFieldEffect {
    String id(); // "C001"

    default List<String> validate(EffectContext ec) {
        return List.of();
    }

    default int onCost(CardCostCtx cc, int currentCost) {
        return currentCost;
    }

    void resolve(EffectContext ec);

    default void resolveLastWords(LastWordsContext lc) {}

    @Override
    default void onEnterField(EffectContext ec, CardInstId sourceCardId) {}

    @Override
    default void onTurnStart(EffectContext ec, CardInstId sourceCardId) {}

    @Override
    default void onTurnEnd(EffectContext ec, CardInstId sourceCardId) {}

    @Override
    default void onLeaveField(EffectContext ec, CardInstId sourceCardId) {}
}
