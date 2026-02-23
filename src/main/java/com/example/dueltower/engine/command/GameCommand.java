package com.example.dueltower.engine.command;

import java.util.List;
import java.util.UUID;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;

public interface GameCommand {
    UUID commandId();
    long expectedVersion();

    List<String> validate(GameState state, EngineContext ctx);

    List<GameEvent> handle(GameState state, EngineContext ctx);
}