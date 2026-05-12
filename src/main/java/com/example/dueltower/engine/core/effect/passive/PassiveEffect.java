package com.example.dueltower.engine.core.effect.passive;

import com.example.dueltower.common.util.Rational;
import com.example.dueltower.engine.core.effect.status.StatusApplyContext;
import com.example.dueltower.engine.core.effect.status.StatusApplyResult;
import com.example.dueltower.engine.model.*;

import java.util.List;

public interface PassiveEffect {
    String id();

    default int onIncomingDamage(PassiveRuntime rt, TargetRef source, TargetRef target, int amount) {
        return amount;
    }

    default int onOutgoingDamage(PassiveRuntime rt, TargetRef source, TargetRef target, int amount) {
        return amount;
    }

    default int onIncomingHeal(PassiveRuntime rt, TargetRef source, TargetRef target, int amount) {
        return amount;
    }

    default int onOutgoingHeal(PassiveRuntime rt, TargetRef source, TargetRef target, int amount) {
        return amount;
    }

    /**
     * 치명타 확률(%)을 조정하는 훅.
     * - kind: "damage" | "heal"
     */
    default int onCriticalChancePercent(PassiveRuntime rt, TargetRef source, TargetRef target, String kind, int currentChance) {
        return currentChance;
    }

    /**
     * 치명타 배율을 조정하는 훅.
     * - kind: "damage" | "heal"
     */
    default Rational onCriticalAmountMultiplier(PassiveRuntime rt, TargetRef source, TargetRef target, String kind, Rational currentMultiplier) {
        return currentMultiplier;
    }

    /**
     * 피격/피회복 대상 관점에서 치명타 확률(%)을 조정하는 훅.
     * - kind: "damage" | "heal"
     */
    default int onIncomingCriticalChancePercent(PassiveRuntime rt, TargetRef source, TargetRef target, String kind, int currentChance) {
        return currentChance;
    }

    /**
     * 피격/피회복 대상 관점에서 치명타 배율을 조정하는 훅.
     * - kind: "damage" | "heal"
     */
    default Rational onIncomingCriticalAmountMultiplier(PassiveRuntime rt, TargetRef source, TargetRef target, String kind, Rational currentMultiplier) {
        return currentMultiplier;
    }

    default int onCost(PassiveRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def, int currentCost) {
        return currentCost;
    }

    default void validatePlayCard(PassiveRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def, List<String> errors) {}

    default void onAfterPlayCard(PassiveRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def) {}

    default int onBeforeApplyStatus(PassiveRuntime rt, StatusApplyContext apply, int currentAmount) {
        return currentAmount;
    }

    default void onAfterApplyStatus(PassiveRuntime rt, StatusApplyContext apply, StatusApplyResult result) {}

    default void onTurnEnd(PassiveRuntime rt, TargetRef owner) {}

    default void onTurnStart(PassiveRuntime rt, TargetRef owner) {}
}
