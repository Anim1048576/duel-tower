package com.example.dueltower.engine.core.effect.card;

public interface ReactiveCardEffect extends CardEffect {
    boolean canReact(ReactionEffectContext rc);

    void resolveReaction(ReactionEffectContext rc);
}
