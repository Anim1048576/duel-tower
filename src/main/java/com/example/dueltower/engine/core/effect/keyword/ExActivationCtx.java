package com.example.dueltower.engine.core.effect.keyword;

import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;

public record ExActivationCtx(
        PlayerState owner,
        Ids.CardInstId cardId,
        boolean exCard,
        ExActivationReason reason,
        int round
) {}
