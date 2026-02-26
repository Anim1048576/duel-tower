package com.example.dueltower.engine.core.effect.keyword;

import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.PlayerState;

/**
 * Context for discard validation.
 * Keep it minimal for now; extend as custom keyword rules expand.
 */
public record DiscardCtx(
        PlayerState player,
        CardInstId cardId,
        DiscardReason reason
) {}
