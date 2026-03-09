package com.example.dueltower.content.card.model;

import com.example.dueltower.content.cardmodifier.cmdb.CardModifierIds;

import java.util.List;
import java.util.Set;

public final class OwnedCardModifierSemantics {
    private static final Set<String> CONCRETE_WEAKENED_IDS = Set.of(
            CardModifierIds.WEAKENED_COST_PLUS_ONE,
            CardModifierIds.WEAKENED_SELF_DAMAGE_10,
            CardModifierIds.WEAKENED_FINAL_HALF,
            CardModifierIds.WEAKENED_RANDOM_ENEMY_ONE,
            CardModifierIds.WEAKENED_DISCARD_ONE_SKILL
    );

    private OwnedCardModifierSemantics() {}

    public static boolean isStrengthened(List<OwnedCardModifier> modifiers) {
        return hasModifier(modifiers, CardModifierIds.STRENGTHENED);
    }

    public static boolean isLockedInDeck(List<OwnedCardModifier> modifiers) {
        return hasModifier(modifiers, CardModifierIds.LOCKED_IN_DECK);
    }

    public static boolean isWeakened(List<OwnedCardModifier> modifiers) {
        if (hasModifier(modifiers, CardModifierIds.WEAKENED)) {
            return true;
        }
        if (modifiers == null || modifiers.isEmpty()) {
            return false;
        }
        for (OwnedCardModifier modifier : modifiers) {
            if (modifier != null && CONCRETE_WEAKENED_IDS.contains(modifier.modifierId())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasModifier(List<OwnedCardModifier> modifiers, String modifierId) {
        if (modifierId == null || modifierId.isBlank() || modifiers == null || modifiers.isEmpty()) {
            return false;
        }
        String normalized = modifierId.trim();
        for (OwnedCardModifier modifier : modifiers) {
            if (modifier != null && normalized.equals(modifier.modifierId())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasConcreteWeakenedModifier(List<OwnedCardModifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) {
            return false;
        }
        for (OwnedCardModifier modifier : modifiers) {
            if (modifier != null && CONCRETE_WEAKENED_IDS.contains(modifier.modifierId())) {
                return true;
            }
        }
        return false;
    }
}
