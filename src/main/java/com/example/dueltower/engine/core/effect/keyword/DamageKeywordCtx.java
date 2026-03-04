package com.example.dueltower.engine.core.effect.keyword;

import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.TargetRef;

/**
 * Context for keyword hooks that affect how damage interacts with defensive status hooks.
 */
public record DamageKeywordCtx(
        TargetRef source,
        Ids.CardInstId cardId,
        TargetRef target
) {}
