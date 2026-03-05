package com.example.dueltower.engine.core.effect.card;

import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.model.Ids.CardInstId;

public interface PersistentFieldEffect {
    default void onEnterField(EffectContext ec, CardInstId sourceCardId) {}
    default void onTurnStart(EffectContext ec, CardInstId sourceCardId) {}
    default void onTurnEnd(EffectContext ec, CardInstId sourceCardId) {}
    default void onLeaveField(EffectContext ec, CardInstId sourceCardId) {}
}
