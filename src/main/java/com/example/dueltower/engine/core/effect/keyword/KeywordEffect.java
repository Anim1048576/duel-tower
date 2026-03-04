package com.example.dueltower.engine.core.effect.keyword;

import com.example.dueltower.engine.model.Zone;

import java.util.List;

/**
 * Runtime behavior for a keyword.
 * NOTE:
 * - We started with discard-related hooks (to replace hard-coded '부동').
 * - Add more hooks (cost, resolveTo override, damage flags, etc.) as needed.
 */
public interface KeywordEffect {
    String id();

    /**
     * Whether this keyword blocks discarding the card in the given context.
     * Used for cheap checks such as effective hand limit.
     */
    default boolean blocksDiscard(KeywordRuntime rt, DiscardCtx c) { return false; }

    /**
     * Validation hook: append human-readable error messages if discard is not allowed.
     */
    default void validateDiscard(KeywordRuntime rt, DiscardCtx c, List<String> errors) {
        if (blocksDiscard(rt, c)) {
            errors.add("discard blocked by keyword: " + rt.id());
        }
    }

    /**
     * Hook to override the destination zone when a card is moved.
     * Implementations should usually return {@code currentTo} unchanged unless the keyword is active.
     */
    default Zone overrideMoveDestination(KeywordRuntime rt, MoveCtx c, Zone currentTo) { return currentTo; }

    default boolean overrideExActivatable(KeywordRuntime rt, ExActivationCtx c, boolean current) { return current; }

    /**
     * Targeting rule hook: if true, global TAUNT constraints are ignored for ENEMY_ONE targeting.
     * (Used by keyword: "명경")
     */
    default boolean ignoresTaunt(KeywordRuntime rt, EnemyOneTargetCtx c) { return false; }

    /**
     * AP payment hook: whether this keyword allows playing even when AP is insufficient.
     * (Used by keyword: "집념")
     */
    default boolean allowsApDebtPayment(KeywordRuntime rt, ApDebtCtx c, int cost, int have) { return false; }

    /**
     * If {@link #allowsApDebtPayment} is true, return how much AP debt should be recorded.
     * (Used by keyword: "집념")
     */
    default int apDebtAmount(KeywordRuntime rt, ApDebtCtx c, int cost, int have) { return 0; }

    /**
     * Validation hook for AP debt payment rules (e.g., once-per-turn constraints).
     */
    default void validateApDebtPayment(KeywordRuntime rt, ApDebtCtx c, int cost, int have, List<String> errors) {}
}
