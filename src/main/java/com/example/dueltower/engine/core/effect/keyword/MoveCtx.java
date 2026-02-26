package com.example.dueltower.engine.core.effect.keyword;

import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Zone;

/**
 * Context for zone movement hooks.
 */
public record MoveCtx(
        PlayerState owner,
        CardInstId cardId,
        Zone from,
        Zone to,
        MoveReason reason
) {}
