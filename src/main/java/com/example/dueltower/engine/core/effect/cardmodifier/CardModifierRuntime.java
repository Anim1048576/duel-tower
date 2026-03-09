package com.example.dueltower.engine.core.effect.cardmodifier;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;

import java.util.List;

public record CardModifierRuntime(
        GameState state,
        EngineContext ctx,
        List<GameEvent> out,
        String source,
        int value
) {}
