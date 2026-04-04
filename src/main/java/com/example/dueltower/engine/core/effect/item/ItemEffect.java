package com.example.dueltower.engine.core.effect.item;

import java.util.List;

public interface ItemEffect {
    String id();

    default boolean requiresTarget() { return false; }

    default void validateUse(UseItemValidationContext ctx, List<String> errors) {}

    void resolveUse(UseItemResolutionContext ctx);
}
