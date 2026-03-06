package com.example.dueltower.engine.core.effect.passive;

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

    default int onCost(PassiveRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def, int currentCost) {
        return currentCost;
    }

    default void validatePlayCard(PassiveRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def, List<String> errors) {}

    default void onAfterPlayCard(PassiveRuntime rt, TargetRef actor, CardInstance ci, CardDefinition def) {}

    default void onTurnEnd(PassiveRuntime rt, TargetRef owner) {}

    default void onTurnStart(PassiveRuntime rt, TargetRef owner) {}
}
