package com.example.dueltower.engine.core.effect.keyword;

import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;

/**
 * Context for post-play keyword hooks.
 *
 * <p>Called after the card cost has been paid but before/around resolution, depending on the caller.
 * Use this to implement "once per turn" usage tracking, AP-debt bookkeeping, etc.
 */
public record AfterPlayCardCtx(
        PlayerState owner,
        Ids.CardInstId cardId,
        int cost,
        int haveBeforePay,
        int debt
) {}
