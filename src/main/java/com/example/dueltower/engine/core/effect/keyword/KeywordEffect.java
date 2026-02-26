package com.example.dueltower.engine.core.effect.keyword;

import java.util.List;

/**
 * Runtime behavior for a keyword.
 * NOTE:
 * - For now we only wire discard-related hooks (to replace hard-coded '부동').
 * - Add more hooks (cost, resolveTo override, damage flags, etc.) as needed.
 */
public interface KeywordEffect {
    String id();

    /**
     * Whether this keyword blocks discarding the card in the given context.
     * Used for cheap checks such as effective hand limit.
     */
    default boolean blocksDiscard(KeywordRuntime rt, DiscardCtx c) {
        return false;
    }

    /**
     * Validation hook: append human-readable error messages if discard is not allowed.
     */
    default void validateDiscard(KeywordRuntime rt, DiscardCtx c, List<String> errors) {
        if (blocksDiscard(rt, c)) {
            errors.add("discard blocked by keyword: " + rt.id());
        }
    }
}
