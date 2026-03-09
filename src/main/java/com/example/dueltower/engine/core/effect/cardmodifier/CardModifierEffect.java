package com.example.dueltower.engine.core.effect.cardmodifier;

import com.example.dueltower.engine.model.TargetRef;

import java.util.List;

public interface CardModifierEffect {
    String id();

    default int modifyCost(CardModifierRuntime rt, ModifyCostCtx c, int currentCost) {
        return currentCost;
    }

    default void validatePlayCard(CardModifierRuntime rt, PlayCardModifierCtx c, List<String> errors) {}

    default void beforeResolvePlayCard(CardModifierRuntime rt, PlayCardModifierCtx c) {}

    default void afterResolvePlayCard(CardModifierRuntime rt, PlayCardModifierCtx c) {}

    default TargetRef resolveEnemyOneTarget(
            CardModifierRuntime rt,
            EnemyOneModifierTargetCtx c,
            TargetRef chosenEnemy,
            List<TargetRef> candidates
    ) {
        return chosenEnemy;
    }

    default int onOutgoingDamage(CardModifierRuntime rt, OutgoingCardValueCtx c, TargetRef target, int amount) {
        return amount;
    }

    default int onOutgoingHeal(CardModifierRuntime rt, OutgoingCardValueCtx c, TargetRef target, int amount) {
        return amount;
    }
}
