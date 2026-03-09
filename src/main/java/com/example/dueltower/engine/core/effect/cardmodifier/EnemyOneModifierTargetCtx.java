package com.example.dueltower.engine.core.effect.cardmodifier;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;

import java.util.List;

public record EnemyOneModifierTargetCtx(
        GameState state,
        EngineContext ctx,
        List<GameEvent> out,
        TargetRef actor,
        CardInstId cardId,
        CardInstance ci,
        CardDefinition def
) {}
