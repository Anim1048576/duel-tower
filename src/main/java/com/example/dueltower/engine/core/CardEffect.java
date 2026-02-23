package com.example.dueltower.engine.core;

import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.List;

public interface CardEffect {
    String id();

    default List<String> validate(GameState state, EngineContext ctx, PlayerId playerId, CardInstId cardId) {
        return List.of();
    }

    void resolve(GameState state, EngineContext ctx, PlayerId playerId, CardInstId cardId, List<GameEvent> out);
}