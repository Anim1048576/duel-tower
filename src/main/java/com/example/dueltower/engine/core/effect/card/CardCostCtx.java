package com.example.dueltower.engine.core.effect.card;

import com.example.dueltower.engine.model.*;

public record CardCostCtx(
        GameState state,
        TargetRef actor,
        Ids.CardInstId cardId,
        CardInstance ci,
        CardDefinition def,
        String source
) {}
