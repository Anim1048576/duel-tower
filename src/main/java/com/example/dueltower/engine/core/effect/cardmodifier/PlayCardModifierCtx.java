package com.example.dueltower.engine.core.effect.cardmodifier;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.LastWordsBatchCollector;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;

import java.util.List;

public record PlayCardModifierCtx(
        GameState state,
        EngineContext ctx,
        List<GameEvent> out,
        TargetRef actor,
        PlayerState actorState,
        CardInstId cardId,
        CardInstance ci,
        CardDefinition def,
        LastWordsBatchCollector lastWordsBatchCollector
) {}
