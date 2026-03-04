package com.example.dueltower.engine.core.effect.keyword;

import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;

/**
 * Context for paying a card cost with AP debt.
 */
public record ApDebtCtx(
        PlayerState owner,
        Ids.CardInstId cardId
) {}
